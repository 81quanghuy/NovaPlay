package vn.iotstar.promotionservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giữ một connection Postgres riêng ở trạng thái {@code LISTEN} và chuyển mỗi notification thành
 * một lượt gửi.
 * <p>
 * Connection này KHÔNG lấy từ HikariCP: nó bị giữ vô hạn, mượn từ pool sẽ làm đói pool.
 * <p>
 * Vòng lặp chặn ở {@code getNotifications(timeout)}, tức là chặn ở tầng socket — hết timeout mà
 * không có notification thì thức dậy để kiểm tra cờ shutdown và KHÔNG phát câu SQL nào. Đây là
 * điểm khác biệt cốt lõi so với {@code OutboxRelayJob} cũ vốn quét bảng mỗi 5 giây.
 */
@Slf4j
public class OutboxNotificationListener implements SmartLifecycle {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final OutboxProperties properties;
    private final OutboxCatchUp catchUp;
    private final OutboxRelayService relayService;
    /** Dùng CHUNG với OutboxEventPublisherImpl và do container quản lý vòng đời — xem OutboxConfiguration. */
    private final Executor relayExecutor;

    private final AtomicInteger failedConnectionAttempts = new AtomicInteger();
    private final AtomicBoolean unsupportedListenReported = new AtomicBoolean();
    private volatile boolean running;
    private volatile Connection currentConnection;
    private Thread listenerThread;

    public OutboxNotificationListener(String jdbcUrl, String username, String password,
                                      OutboxProperties properties, OutboxCatchUp catchUp,
                                      OutboxRelayService relayService, Executor relayExecutor) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.properties = properties;
        this.catchUp = catchUp;
        this.relayService = relayService;
        this.relayExecutor = relayExecutor;
    }

    public int getFailedConnectionAttempts() {
        return failedConnectionAttempts.get();
    }

    @Override
    public void start() {
        running = true;
        listenerThread = new Thread(this::listenLoop, "outbox-listen");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("Đã khởi động listener outbox trên kênh {}", properties.getChannel());
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(currentConnection);   // phá vỡ lệnh đọc đang chặn ở socket
        if (listenerThread != null) {
            listenerThread.interrupt();    // phá vỡ Thread.sleep() lúc đang backoff chờ reconnect
            try {
                listenerThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // KHÔNG shutdown relayExecutor ở đây: nó là bean dùng chung với OutboxEventPublisherImpl,
        // vòng đời do container quản lý (destroyMethod). Đóng nó ở đây sẽ giết luôn đường relay
        // sau-commit của publisher.
        log.info("Đã dừng listener outbox");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listenLoop() {
        long reconnectDelay = properties.getReconnectInitialDelay().toMillis();

        while (running) {
            // Đặt lại mỗi vòng: câu hỏi là "connection LẦN NÀY có phục vụ được không", không phải
            // "đã từng có connection nào phục vụ được chưa".
            AtomicBoolean served = new AtomicBoolean();
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                if (!running) {
                    // stop() đã được gọi trong lúc đang mở connection này (running bị đọc là true
                    // ở while phía trên trước khi stop() chạy). Không LISTEN, không catchUp — thoát
                    // ngay để không quét bù cả backlog trong lúc pod đang tắt.
                    return;
                }
                currentConnection = connection;
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + properties.getChannel());
                }

                // Mọi notification phát trong lúc chưa có connection đều đã mất, nên phải quét bù
                // ngay sau khi nối được — kể cả ở lần nối đầu tiên lúc khởi động.
                catchUp.run();

                consumeNotifications(connection.unwrap(PGConnection.class), served);
            } catch (SQLException e) {
                if (!running) {
                    return;
                }
                failedConnectionAttempts.incrementAndGet();
                reportUnsupportedListenOnce(e);

                // Reset backoff CHỈ khi connection đã thật sự phục vụ được ít nhất một lượt chờ
                // notification. Reset ngay sau khi nối được (như bản trước) là sai: nếu mọi
                // connection đều chết NGAY SAU lúc dựng xong — đúng những gì một pooler như
                // Supavisor/PgBouncer gây ra — thì backoff không bao giờ tăng, và vòng lặp biến
                // thành cơn bão mở connection mới mỗi giây vào Postgres, vĩnh viễn.
                if (served.get()) {
                    reconnectDelay = properties.getReconnectInitialDelay().toMillis();
                }
                log.warn("Mất connection LISTEN outbox, thử lại sau {}ms: {}", reconnectDelay, e.getMessage());
                sleep(reconnectDelay);
                reconnectDelay = Math.min(reconnectDelay * 2, properties.getReconnectMaxDelay().toMillis());
            } catch (RuntimeException e) {
                // catchUp.run() (và mọi thứ khác trong try) có thể ném exception unchecked, ví dụ
                // DataAccessException của Spring khi Postgres tạm thời từ chối câu nhận việc — đây
                // KHÔNG phải SQLException nên không rơi vào nhánh trên. Nếu để lọt ra ngoài,
                // listenLoop() kết thúc, thread outbox-listen chết lặng lẽ, và không có job định kỳ
                // nào còn lại để cứu: toàn bộ việc gửi outbox của pod này dừng vĩnh viễn. Vì vậy bọc
                // riêng ở đây và áp dụng cùng backoff để vòng lặp reconnect vẫn tiếp tục.
                if (!running) {
                    return;
                }
                log.error("Lỗi không mong đợi trong vòng lặp listener outbox, thử lại sau {}ms",
                        reconnectDelay, e);
                sleep(reconnectDelay);
                reconnectDelay = Math.min(reconnectDelay * 2, properties.getReconnectMaxDelay().toMillis());
            } finally {
                currentConnection = null;
            }
        }
    }

    private void consumeNotifications(PGConnection pgConnection, AtomicBoolean served) throws SQLException {
        int timeoutMillis = (int) properties.getListenTimeout().toMillis();
        while (running) {
            PGNotification[] notifications = pgConnection.getNotifications(timeoutMillis);
            // Về được tới đây nghĩa là connection đã đi trọn một lượt chờ mà không vỡ — đủ để coi
            // là lành mạnh và cho phép reset backoff nếu sau này nó đứt.
            served.set(true);
            if (notifications == null) {
                continue;   // hết timeout, không có gì — không phát SQL nào
            }
            for (PGNotification notification : notifications) {
                submit(notification.getParameter());
            }
        }
    }

    /**
     * Lỗi {@code Unknown Response Type S} không phải sự cố mạng thoáng qua — nó có nghĩa là đầu bên
     * kia gửi một message {@code ParameterStatus} trong lúc connection đang chờ notification, và
     * pgjdbc chỉ xử lý {@code 'A'/'E'/'N'} ở nhánh này nên coi connection là hỏng. Nguồn phát ra
     * message đó luôn là một connection pooler (Supavisor của Supabase, PgBouncer), tức là LISTEN
     * sẽ KHÔNG BAO GIỜ hoạt động qua đường này dù thử lại bao nhiêu lần. Log một lần ở mức ERROR
     * kèm cách khắc phục, thay vì để WARN lặp lại che mất nguyên nhân thật.
     */
    private void reportUnsupportedListenOnce(SQLException e) {
        String message = e.getMessage();
        if (message != null && message.contains("Unknown Response Type")
                && unsupportedListenReported.compareAndSet(false, true)) {
            log.error("Đích Postgres đang dùng KHÔNG hỗ trợ LISTEN/NOTIFY (dấu hiệu: '{}'). "
                            + "Gần như chắc chắn connection đang đi qua connection pooler. "
                            + "LISTEN sẽ không bao giờ nhận được notification qua đường này; outbox "
                            + "chỉ còn được nhặt bởi catch-up mỗi lần kết nối lại. Cần trỏ listener "
                            + "sang connection Postgres trực tiếp, hoặc chuyển sang kênh đánh thức khác.",
                    message);
        }
    }

    private void submit(String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            relayExecutor.execute(() -> relayService.relay(id));
        } catch (IllegalArgumentException e) {
            log.warn("Notification outbox mang payload không phải UUID: {}", rawId);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Đóng để phá vỡ lệnh đọc đang chặn; lỗi ở đây không có ý nghĩa gì.
            }
        }
    }
}

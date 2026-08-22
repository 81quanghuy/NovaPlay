package vn.iotstar.authservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quét bù định kỳ cho outbox — lưới an toàn phía sau {@link OutboxEventPublisherImpl}.
 * <p>
 * Đường chính là hook {@code afterCommit}: ghi row xong, COMMIT xong thì đẩy thẳng id sang relay
 * trong cùng JVM. Lưới này tồn tại cho đúng một tình huống mà đường chính không phủ được: pod
 * chết SAU khi COMMIT nhưng TRƯỚC khi relay kịp gửi. Vì vậy chu kỳ mặc định để thưa (60s) — kéo
 * ngắn lại không làm event tới nhanh hơn, chỉ tốn thêm query.
 * <p>
 * Dùng {@link SmartLifecycle} + scheduler riêng thay vì {@code @Scheduled}: package outbox này là
 * bản sao độc lập của từng service, không được phép giả định service chủ đã bật
 * {@code @EnableScheduling} (promotion-service thì chưa).
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxSweepJob implements SmartLifecycle {

    private final OutboxCatchUp catchUp;
    private final OutboxProperties properties;

    private volatile boolean running;
    private ScheduledExecutorService scheduler;

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-sweep");
            t.setDaemon(true);
            return t;
        });
        long intervalMillis = properties.getSweepInterval().toMillis();
        // initialDelay = 0: lượt đầu chính là bước quét bù lúc khởi động mà listener LISTEN trước
        // đây đảm nhiệm. Bỏ nó đi thì backlog của pod trước phải chờ hết một chu kỳ mới được nhặt.
        scheduler.scheduleWithFixedDelay(this::sweepQuietly, 0, intervalMillis, TimeUnit.MILLISECONDS);
        running = true;
        log.info("Đã khởi động sweep outbox, chu kỳ {}ms", intervalMillis);
    }

    @Override
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        log.info("Đã dừng sweep outbox");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Nuốt exception là BẮT BUỘC, không phải cẩu thả: {@code scheduleWithFixedDelay} HỦY VĨNH VIỄN
     * task ngay khi runnable ném ra ngoài, và không log gì cả. Để lọt một lỗi DB thoáng qua là mất
     * luôn lưới an toàn của pod này cho tới lần restart sau, trong im lặng.
     */
    private void sweepQuietly() {
        try {
            catchUp.run();
        } catch (Exception e) {
            log.error("Sweep outbox thất bại, sẽ thử lại ở chu kỳ sau", e);
        }
    }
}

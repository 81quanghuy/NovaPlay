package vn.iotstar.authservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Quét bảng outbox theo chu kỳ, độc lập hoàn toàn với LISTEN/NOTIFY.
 * <p>
 * Tồn tại vì hai lý do tách biệt:
 * <ol>
 *   <li><b>Postgres managed không phải lúc nào cũng có LISTEN.</b> Endpoint đi qua PgBouncer ở
 *       chế độ transaction pooling không hỗ trợ nó, còn endpoint serverless tự ngủ (Neon) thì
 *       cắt connection liên tục. Với {@code listenEnabled=false}, đây là cơ chế DUY NHẤT đẩy
 *       outbox đi.</li>
 *   <li><b>Notification của Postgres không bền vững.</b> Kể cả khi LISTEN chạy tốt, row được
 *       INSERT đúng lúc connection đứt là mất tín hiệu vĩnh viễn. Trước đây chỉ có catch-up lúc
 *       khởi động/reconnect nhặt được chúng — nghĩa là nếu connection không bao giờ đứt lại,
 *       row đó nằm im vô thời hạn.</li>
 * </ol>
 * Không cần distributed lock: {@link OutboxCatchUp#run()} nhận việc qua
 * {@code OutboxDao#claimBatch} vốn dùng {@code FOR UPDATE SKIP LOCKED}, nên nhiều pod quét cùng
 * lúc sẽ chia nhau row thay vì gửi trùng.
 */
@Slf4j
public class OutboxPoller implements SmartLifecycle {

    private final OutboxCatchUp catchUp;
    private final OutboxProperties properties;

    private volatile boolean running;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public OutboxPoller(OutboxCatchUp catchUp, OutboxProperties properties) {
        this.catchUp = catchUp;
        this.properties = properties;
    }

    @Override
    public void start() {
        long intervalMs = properties.getPollInterval().toMillis();
        if (intervalMs <= 0) {
            log.info("Poller outbox tắt (novaplay.outbox.poll-interval=0)");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-poll");
            t.setDaemon(true);
            return t;
        });
        // scheduleWithFixedDelay chứ không phải AtRate: một lượt quét chậm không được phép làm
        // các lượt sau dồn đống lên nhau.
        task = scheduler.scheduleWithFixedDelay(this::pollOnce, intervalMs, intervalMs,
                TimeUnit.MILLISECONDS);
        running = true;
        log.info("Đã khởi động poller outbox, chu kỳ {}ms (listenEnabled={})",
                intervalMs, properties.isListenEnabled());
    }

    private void pollOnce() {
        try {
            int picked = catchUp.run();
            if (picked > 0) {
                log.debug("Poller outbox đã nhặt {} row", picked);
            }
        } catch (Exception e) {
            // Nuốt mọi lỗi: ném ra khỏi scheduleWithFixedDelay sẽ HUỶ task vĩnh viễn, và khi đó
            // outbox ngừng chảy im lặng cho tới lần restart pod kế tiếp.
            log.error("Lượt quét outbox thất bại, sẽ thử lại ở chu kỳ sau", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (task != null) {
            task.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

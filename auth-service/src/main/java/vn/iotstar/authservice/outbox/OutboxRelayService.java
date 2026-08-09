package vn.iotstar.authservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chuyển một bản ghi outbox đã commit sang Kafka.
 * <p>
 * Tách làm hai bước để đường notification và đường catch-up dùng chung phần gửi mà mỗi lượt gửi
 * chỉ nhận việc đúng một lần: {@link #relay(UUID)} tự nhận việc rồi gọi {@link #send(OutboxRecord)};
 * còn catch-up đã nhận việc bằng chính câu {@code claimBatch} nên gọi thẳng {@code send}.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxDao dao;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ScheduledExecutorService retryScheduler;
    private final OutboxMetrics metrics;
    private final OutboxProperties properties;

    /** Đường đi khi nhận được notification: phải tự tranh quyền xử lý trước. */
    public void relay(UUID id) {
        dao.claimById(id).ifPresent(this::send);
    }

    /**
     * Gửi một row đã được nhận việc. Không {@code .get()} ở đâu cả — bản cũ chặn tới 10 giây mỗi
     * event ngay bên trong một transaction bọc cả vòng lặp, đủ để cạn connection pool khi Kafka treo.
     */
    public void send(OutboxRecord record) {
        kafkaTemplate.send(record.topic(), record.key(), record.payload())
                .whenComplete((result, throwable) -> {
                    // whenComplete chạy trên thread của Kafka client (hoặc thread hiện tại nếu future
                    // đã xong); future trả về của whenComplete không ai quan sát, và
                    // ScheduledExecutorService cũng không tự log exception ném ra từ Runnable đã nộp.
                    // Không bọc try/catch ở đây thì một lỗi DB thoáng qua trong dao.delete/recordError/
                    // markFailed hay retryScheduler.schedule sẽ biến mất hoàn toàn — event kẹt lại mà
                    // không có log nào giải thích tại sao.
                    try {
                        if (throwable == null) {
                            dao.delete(record.id());
                            metrics.published();
                            log.debug("Đã gửi outbox: id={}, topic={}", record.id(), record.topic());
                        } else {
                            onFailure(record, throwable);
                        }
                    } catch (Exception e) {
                        log.error("Lỗi không mong đợi khi xử lý kết quả gửi outbox: id={}, topic={}",
                                record.id(), record.topic(), e);
                    }
                });
    }

    private void onFailure(OutboxRecord record, Throwable throwable) {
        metrics.relayFailed();
        String message = truncate(String.valueOf(throwable.getMessage()));

        if (record.attempts() >= properties.getMaxAttempts()) {
            dao.markFailed(record.id(), message);
            metrics.terminallyFailed();
            log.error("Outbox hỏng vĩnh viễn sau {} lượt: id={}, topic={}, lỗi={}",
                    record.attempts(), record.id(), record.topic(), message);
            return;
        }

        dao.recordError(record.id(), message);

        // next_attempt_at đã được câu lệnh nhận việc đẩy về tương lai, nên chỉ cần hẹn đúng mốc đó.
        // Đây là hẹn giờ một phát cho một event cụ thể, không phải vòng quét bảng.
        long delayMillis = Math.max(0, Duration.between(Instant.now(), record.nextAttemptAt()).toMillis());
        retryScheduler.schedule(() -> relay(record.id()), delayMillis, TimeUnit.MILLISECONDS);

        log.warn("Gửi outbox hỏng, sẽ thử lại sau {}ms: id={}, lượt={}, lỗi={}",
                delayMillis, record.id(), record.attempts(), message);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}

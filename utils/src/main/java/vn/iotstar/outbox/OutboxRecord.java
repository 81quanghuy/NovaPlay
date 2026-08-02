package vn.iotstar.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng outbox đã được nhận việc và đang chờ gửi sang Kafka.
 * <p>
 * Cố ý là record chứ không phải JPA entity: mọi truy cập tới bảng đều đi qua {@link OutboxDao}
 * bằng SQL thuần, vì câu lệnh trung tâm của thiết kế là {@code UPDATE ... RETURNING} mà Hibernate
 * không thực thi được qua {@code getResultList()}.
 *
 * @param nextAttemptAt mốc được phép thử lại, đã do câu lệnh nhận việc đẩy về tương lai
 */
public record OutboxRecord(
        UUID id,
        String topic,
        String key,
        String payload,
        int attempts,
        Instant nextAttemptAt) {
}

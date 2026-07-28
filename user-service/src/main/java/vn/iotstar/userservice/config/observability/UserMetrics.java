package vn.iotstar.userservice.config.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Bộ đếm nghiệp vụ được Prometheus thu thập qua {@code /actuator/prometheus}.
 * <p>
 * Những chỉ số này trả lời các câu hỏi mà metric HTTP thuần không trả lời được: người dùng
 * có thực sự dùng tính năng không, và event Kafka có đang thất bại một cách âm thầm không.
 */
@Component
@Getter
public class UserMetrics {

    private final Counter profileUpdated;
    private final Counter favoriteAdded;
    private final Counter favoriteRemoved;
    private final Counter watchProgressUpserted;
    private final Counter avatarUploadRequested;
    private final Counter kafkaEventProcessed;
    private final Counter kafkaEventFailed;
    private final Counter kafkaEventDuplicate;

    public UserMetrics(MeterRegistry registry) {
        this.profileUpdated = Counter.builder("user.profile.updated")
                .description("Số lần hồ sơ người dùng được cập nhật").register(registry);
        this.favoriteAdded = Counter.builder("user.favorite.added")
                .description("Số nội dung được thêm vào danh sách yêu thích").register(registry);
        this.favoriteRemoved = Counter.builder("user.favorite.removed")
                .description("Số nội dung bị xoá khỏi danh sách yêu thích").register(registry);
        this.watchProgressUpserted = Counter.builder("user.watch_progress.upserted")
                .description("Số lần tiến độ xem được ghi nhận").register(registry);
        this.avatarUploadRequested = Counter.builder("user.avatar.upload_requested")
                .description("Số yêu cầu cấp URL upload avatar").register(registry);
        this.kafkaEventProcessed = Counter.builder("user.kafka.event.processed")
                .description("Số event Kafka được xử lý thành công").register(registry);
        this.kafkaEventFailed = Counter.builder("user.kafka.event.failed")
                .description("Số event Kafka xử lý thất bại và được chuyển cho error handler")
                .register(registry);
        this.kafkaEventDuplicate = Counter.builder("user.kafka.event.duplicate")
                .description("Số event Kafka bị bỏ qua vì đã được xử lý trước đó").register(registry);
    }
}

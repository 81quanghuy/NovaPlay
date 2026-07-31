package vn.iotstar.notificationservice.config.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.model.enums.ChannelType;

/**
 * Bộ đếm nghiệp vụ được Prometheus thu thập qua {@code /actuator/prometheus}.
 * <p>
 * Service này không có traffic HTTP đáng kể — phần lớn công việc chạy trên luồng tiêu thụ Kafka —
 * nên nếu không có những chỉ số này thì một kênh hỏng hoàn toàn vẫn im lặng: không request lỗi,
 * không mã 5xx, chỉ có người dùng không nhận được thông báo.
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void eventProcessed(String topic) {
        counter("notification.event.processed", "Số sự kiện được xử lý xong", "topic", topic).increment();
    }

    public void eventFailed(String topic) {
        counter("notification.event.failed", "Số sự kiện xử lý thất bại và chuyển cho error handler",
                "topic", topic).increment();
    }

    public void eventDuplicate(String topic) {
        counter("notification.event.duplicate", "Số sự kiện bị bỏ qua vì mọi kênh đã gửi trước đó",
                "topic", topic).increment();
    }

    /** Sự kiện rơi xuống topic chết. Đây là chỉ số nên gắn cảnh báo trước tiên. */
    public void eventDeadLettered(String topic) {
        counter("notification.kafka.event.dlt", "Số sự kiện nằm lại ở topic chết", "topic", topic)
                .increment();
    }

    public void channelSent(ChannelType channel) {
        counter("notification.channel.sent", "Số thông báo gửi thành công theo kênh",
                "channel", channel.name()).increment();
    }

    public void channelFailed(ChannelType channel) {
        counter("notification.channel.failed", "Số thông báo gửi thất bại theo kênh",
                "channel", channel.name()).increment();
    }

    public void channelSkipped(ChannelType channel) {
        counter("notification.channel.skipped", "Số thông báo bị bỏ qua vì kênh đã gửi trước đó",
                "channel", channel.name()).increment();
    }

    /** Đo thời gian giao thiệp với máy chủ SMTP — nguồn chậm chạp dễ đoán nhất của service này. */
    public Timer emailSendTimer() {
        return Timer.builder("notification.email.send")
                .description("Thời gian gửi một email qua SMTP")
                .register(registry);
    }

    private Counter counter(String name, String description, String tagKey, String tagValue) {
        return Counter.builder(name).description(description).tag(tagKey, tagValue).register(registry);
    }
}

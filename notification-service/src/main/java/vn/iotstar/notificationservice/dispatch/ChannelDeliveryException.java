package vn.iotstar.notificationservice.dispatch;

import vn.iotstar.notificationservice.model.enums.ChannelType;

import java.util.Map;

/**
 * Có ít nhất một kênh gửi thất bại.
 * <p>
 * Ném ra để Kafka thử lại cả bản ghi. Các kênh đã gửi thành công giữ nguyên khoá chống trùng nên
 * lượt thử lại bỏ qua chúng và chỉ chạm tới kênh còn hỏng.
 */
public class ChannelDeliveryException extends RuntimeException {

    private final transient Map<ChannelType, RuntimeException> failures;

    public ChannelDeliveryException(Map<ChannelType, RuntimeException> failures) {
        super("Không gửi được qua kênh: " + failures.keySet());
        this.failures = Map.copyOf(failures);
        // Gắn nguyên nhân đầu tiên làm cause để stack trace trong log và trong header của topic
        // chết chỉ ra được lỗi gốc, thay vì dừng ở đúng lớp bọc này.
        failures.values().stream().findFirst().ifPresent(this::initCause);
    }

    public Map<ChannelType, RuntimeException> failures() {
        return failures;
    }
}

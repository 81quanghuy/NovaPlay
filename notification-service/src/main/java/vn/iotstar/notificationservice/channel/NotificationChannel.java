package vn.iotstar.notificationservice.channel;

import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.ChannelType;

/**
 * Một đường phát thông báo tới người dùng.
 * <p>
 * Thêm kênh mới (WebSocket, FCM...) chỉ cần thêm một bean hiện thực giao diện này và khai báo kênh
 * đó trong {@link ChannelRoutingPolicy}; dispatcher và consumer không phải sửa gì.
 */
public interface NotificationChannel {

    ChannelType type();

    /**
     * Phát thông báo. Ném {@link RuntimeException} nếu thất bại — dispatcher bắt, ghi nhận và vẫn
     * thử các kênh còn lại.
     */
    void send(NotificationRequest request);

    /**
     * Sau khi {@link #send} ném lỗi, có được phép trả lại khoá chống trùng để thử lại không?
     * <p>
     * Mặc định là có. Kênh nào không tự chứng minh được "chưa phát đi" phải trả về {@code false}
     * cho những lỗi đó, nếu không lần thử lại sẽ tạo ra bản sao — với email nghĩa là người dùng
     * nhận thêm một thư nữa.
     */
    default boolean isRetryable(RuntimeException failure) {
        return true;
    }
}

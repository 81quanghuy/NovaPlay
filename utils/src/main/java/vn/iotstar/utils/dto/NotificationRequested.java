package vn.iotstar.utils.dto;

import java.util.Map;
import java.util.Set;

/**
 * Yêu cầu gửi thông báo, không ràng buộc vào kênh cụ thể.
 * <p>
 * Khác với {@link EmailOtpRequested} vốn chỉ mô tả được đúng một kênh, bản ghi này để service
 * phát sự kiện mô tả <em>cái cần báo</em> còn notification-service quyết định <em>gửi qua đâu</em>.
 *
 * @param messageId khoá idempotency. Producer phải sinh giá trị ổn định (UUID lưu kèm trong outbox),
 *                  không được sinh lại mỗi lần thử gửi, nếu không cơ chế chống trùng vô hiệu.
 * @param userId    có thể null nếu bên phát chỉ biết email.
 * @param userEmail bắt buộc — đây là khoá mà người nhận dùng để truy vấn thông báo của mình.
 * @param type      tên hằng số của {@code NotificationType} phía notification-service.
 * @param channels  danh sách kênh mong muốn; để null thì service tự quyết theo chính sách định tuyến.
 * @param locale    thẻ ngôn ngữ BCP 47, ví dụ {@code vi-VN}. Null sẽ rơi về mặc định của hệ thống.
 * @param variables biến để render tiêu đề và nội dung.
 */
public record NotificationRequested(
        String messageId,
        String userId,
        String userEmail,
        String type,
        Set<String> channels,
        String locale,
        Map<String, String> variables
) {}

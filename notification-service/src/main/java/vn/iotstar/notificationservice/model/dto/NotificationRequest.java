package vn.iotstar.notificationservice.model.dto;

import lombok.Builder;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Một yêu cầu gửi thông báo đã được chuẩn hoá, dùng nội bộ trong service.
 * <p>
 * Consumer dịch mọi payload Kafka (mỗi topic một dạng) về đúng bản ghi này, nhờ đó dispatcher và
 * các kênh không cần biết sự kiện đến từ topic nào.
 *
 * @param dedupKey  khoá chống trùng đã được tính sẵn và ổn định qua các lần nhận lại. Đây là dữ
 *                  liệu quan trọng nhất của bản ghi: sai khoá là hoặc gửi trùng, hoặc mất thông báo.
 * @param userEmail bắt buộc. Khoá để người nhận truy vấn thông báo in-app của mình.
 * @param channels  các kênh cần gửi; đã được chính sách định tuyến quyết định xong.
 */
@Builder
public record NotificationRequest(
        String dedupKey,
        String messageId,
        String userId,
        String userEmail,
        NotificationType type,
        Set<ChannelType> channels,
        Locale locale,
        Map<String, String> variables
) {
    public String variable(String name) {
        return variables == null ? null : variables.get(name);
    }

    public String variableOrDefault(String name, String fallback) {
        String value = variable(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

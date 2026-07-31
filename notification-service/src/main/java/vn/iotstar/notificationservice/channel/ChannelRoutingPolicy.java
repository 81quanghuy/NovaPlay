package vn.iotstar.notificationservice.channel;

import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.model.enums.NotificationType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Quyết định mỗi loại thông báo được gửi qua những kênh nào.
 */
@Component
public class ChannelRoutingPolicy {

    private static final Map<NotificationType, Set<ChannelType>> ROUTES = Map.of(
            // OTP cố tình không vào in-app: đặt mã xác thực vào một danh sách đọc lại được sau này
            // làm mất ý nghĩa "một lần" của nó, và bất kỳ ai mượn được phiên đăng nhập đều đọc lại được.
            NotificationType.OTP, EnumSet.of(ChannelType.EMAIL),
            NotificationType.PASSWORD_RESET, EnumSet.of(ChannelType.EMAIL),
            NotificationType.ACCOUNT_ACTIVATED, EnumSet.of(ChannelType.EMAIL, ChannelType.IN_APP),
            NotificationType.GENERIC, EnumSet.of(ChannelType.IN_APP));

    private static final Set<ChannelType> DEFAULT_ROUTE = EnumSet.of(ChannelType.IN_APP);

    public Set<ChannelType> channelsFor(NotificationType type) {
        return ROUTES.getOrDefault(type, DEFAULT_ROUTE);
    }

    /**
     * Phân giải danh sách kênh do bên phát sự kiện chỉ định.
     * <p>
     * Rỗng hoặc null thì rơi về chính sách mặc định. Tên kênh không hợp lệ bị bỏ qua thay vì ném
     * lỗi — một producer viết sai chính tả không nên làm chết cả luồng tiêu thụ; và nếu lọc xong
     * không còn kênh nào thì vẫn quay về chính sách mặc định để thông báo không biến mất.
     */
    public Set<ChannelType> resolve(NotificationType type, Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return channelsFor(type);
        }
        Set<ChannelType> resolved = EnumSet.noneOf(ChannelType.class);
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                resolved.add(ChannelType.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // kênh không tồn tại — bỏ qua
            }
        }
        return resolved.isEmpty() ? channelsFor(type) : resolved;
    }
}

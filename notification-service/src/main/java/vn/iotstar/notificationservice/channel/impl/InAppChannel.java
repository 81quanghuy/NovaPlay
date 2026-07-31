package vn.iotstar.notificationservice.channel.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.channel.NotificationChannel;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.entity.Notification;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.repository.NotificationRepository;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    /** Đủ dài để người dùng còn đọc lại lịch sử gần đây, đủ ngắn để collection không phình vô hạn. */
    private static final Duration RETENTION = Duration.ofDays(90);

    private final NotificationRepository repository;
    private final MessageSource messageSource;

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    public void send(NotificationRequest request) {
        Notification notification = Notification.builder()
                // Đây là toàn bộ cơ chế chống trùng của kênh này: _id trùng thì insert() ném
                // DuplicateKeyException, dispatcher không cần khoá Redis riêng cho kênh này.
                .id(request.dedupKey() + ":" + type())
                .messageId(request.messageId())
                .userId(request.userId())
                .userEmail(request.userEmail())
                .type(request.type())
                .title(renderTitle(request))
                .body(renderBody(request))
                .data(request.variables() == null ? java.util.Map.of() : request.variables())
                .expiresAt(Instant.now().plus(RETENTION))
                .build();

        try {
            repository.insert(notification);
        } catch (DuplicateKeyException alreadyDelivered) {
            log.info("Bỏ qua trùng ở kênh IN_APP: id={}", notification.getId());
        }
    }

    // insert() chỉ ném ra ngoài send() khi Mongo hỏng/mất kết nối trước khi có bất kỳ ghi nào xảy
    // ra — luôn an toàn để thử lại, nên dùng mặc định isRetryable()=true, không cần ghi đè.
    // DuplicateKeyException không bao giờ thoát ra khỏi send() vì đã bị bắt ở trên.

    /**
     * Nội dung do bên phát sự kiện cung cấp được ưu tiên hơn bundle i18n.
     * <p>
     * Với các loại cố định (OTP, kích hoạt tài khoản), service biết trước phải hiển thị gì nên
     * bundle là nguồn đúng. Nhưng với {@code GENERIC} thì ngược lại: chỉ producer mới biết nội
     * dung, và bundle chỉ có một chuỗi giữ chỗ. Bỏ qua biến của producer ở đây nghĩa là mọi
     * thông báo nghiệp vụ đều hiện ra như nhau — không dùng được.
     */
    private String renderTitle(NotificationRequest request) {
        String supplied = request.variable("title");
        return supplied != null && !supplied.isBlank()
                ? supplied
                : messageSource.getMessage(request.type().titleKey(), null, request.locale());
    }

    private String renderBody(NotificationRequest request) {
        String supplied = request.variable("body");
        if (supplied != null && !supplied.isBlank()) {
            return supplied;
        }
        Object[] args = {request.variableOrDefault("username", request.userEmail())};
        return messageSource.getMessage(request.type().bodyKey(), args, request.locale());
    }
}

package vn.iotstar.notificationservice.channel.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.channel.NotificationChannel;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.service.MailSender;

@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final MailSender mailSender;

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(NotificationRequest request) {
        mailSender.send(request);
        // MailSenderImpl chỉ ném MailDeliveryException cho các lỗi xảy ra trước khi SMTP nhận thư
        // (render hỏng hoặc bị từ chối ở tầng transport), nên mọi lỗi thoát ra khỏi send() ở đây
        // đều an toàn để dispatcher trả lại khoá chống trùng và thử lại — dùng mặc định
        // isRetryable()=true, không cần ghi đè.
    }
}

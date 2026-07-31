package vn.iotstar.notificationservice.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.channel.ChannelRoutingPolicy;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.dispatch.NotificationDispatcher;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.AuditLogger;
import vn.iotstar.utils.constants.TopicNames;
import vn.iotstar.utils.dto.EmailOtpRequested;
import vn.iotstar.utils.dto.NotificationRequested;
import vn.iotstar.utils.dto.UserRegister;

import java.util.Locale;
import java.util.Map;

/**
 * Nhận sự kiện từ ba topic khác nhau, dịch mỗi loại về đúng một {@link NotificationRequest}
 * chuẩn hoá rồi giao cho {@link NotificationDispatcher} — bản thân các listener không biết gì về
 * kênh gửi hay khoá chống trùng theo kênh, chỉ chịu trách nhiệm xác thực payload và tính
 * {@code dedupKey} theo đúng nguồn gốc của từng topic.
 * <p>
 * Các listener cố tình KHÔNG bắt exception khi gọi dispatcher: {@code DefaultErrorHandler} cấu
 * hình trong {@code KafkaConsumerConfig} mới là nơi chịu trách nhiệm retry rồi đẩy message hỏng
 * sang topic {@code .DLT}. Nuốt exception ở đây sẽ vô hiệu hoá toàn bộ cơ chế đó.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi-VN");

    private final NotificationDispatcher dispatcher;
    private final ChannelRoutingPolicy routingPolicy;
    private final NotificationMetrics metrics;
    private final AuditLogger auditLogger;

    @KafkaListener(topics = TopicNames.SEND_EMAIL, containerFactory = "emailOtpKafkaListenerContainerFactory")
    public void handleEmailOtp(EmailOtpRequested evt,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
            Acknowledgment ack) {

        if (evt == null) throw new IllegalArgumentException("Null payload");
        Map<String, String> vars = evt.variables() == null ? Map.of() : evt.variables();
        if (vars.get("otp") == null || evt.email() == null || evt.email().isBlank()) {
            throw new IllegalArgumentException("Thiếu otp hoặc email");
        }

        // Không có khoá nghiệp vụ tự nhiên cho OTP — mỗi lần yêu cầu là một sự kiện độc lập, nên
        // rơi thẳng từ messageId xuống toạ độ bản ghi nếu producer lỡ thiếu messageId.
        String dedupKey = resolveDedupKey(evt.messageId(), null, topic, partition, offset);
        NotificationRequest request = NotificationRequest.builder()
                .dedupKey(dedupKey)
                .messageId(evt.messageId())
                .userId(evt.userId())
                .userEmail(evt.email())
                .type(NotificationType.OTP)
                .channels(routingPolicy.channelsFor(NotificationType.OTP))
                .locale(resolveLocale(vars.get("locale")))
                .variables(vars)
                .build();

        process(topic, dedupKey, request, ack);
    }

    @KafkaListener(topics = TopicNames.ACTIVATE_ACCOUNT, containerFactory = "userRegisterKafkaListenerContainerFactory")
    public void handleUserRegister(UserRegister evt,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
            Acknowledgment ack) {

        if (evt == null) throw new IllegalArgumentException("Null payload");
        if (evt.email() == null || evt.email().isBlank()) {
            throw new IllegalArgumentException("Invalid email");
        }

        // UserRegister không mang messageId — auth-service có thể publish lại qua outbox và mỗi
        // lần thử lại sẽ rơi vào một offset khác nhau. Dùng khoá nghiệp vụ "kích hoạt một lần cho
        // mỗi email" thay vì toạ độ bản ghi, nếu không thư chào mừng có thể bị gửi lặp lại.
        String naturalKey = "activate-account:" + evt.email();
        String dedupKey = resolveDedupKey(null, naturalKey, topic, partition, offset);
        NotificationRequest request = NotificationRequest.builder()
                .dedupKey(dedupKey)
                .userEmail(evt.email())
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .channels(routingPolicy.channelsFor(NotificationType.ACCOUNT_ACTIVATED))
                .locale(DEFAULT_LOCALE)
                .variables(Map.of("username", evt.username() == null ? evt.email() : evt.username()))
                .build();

        process(topic, dedupKey, request, ack);
    }

    @KafkaListener(topics = TopicNames.NOTIFICATION_REQUESTED,
            containerFactory = "notificationRequestedKafkaListenerContainerFactory")
    public void handleNotificationRequested(NotificationRequested evt,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
            Acknowledgment ack) {

        if (evt == null) throw new IllegalArgumentException("Null payload");
        if (evt.userEmail() == null || evt.userEmail().isBlank()) {
            throw new IllegalArgumentException("Invalid userEmail");
        }

        NotificationType type = NotificationType.from(evt.type());
        String dedupKey = resolveDedupKey(evt.messageId(), null, topic, partition, offset);
        NotificationRequest request = NotificationRequest.builder()
                .dedupKey(dedupKey)
                .messageId(evt.messageId())
                .userId(evt.userId())
                .userEmail(evt.userEmail())
                .type(type)
                .channels(routingPolicy.resolve(type, evt.channels()))
                .locale(resolveLocale(evt.locale()))
                .variables(evt.variables() == null ? Map.of() : evt.variables())
                .build();

        process(topic, dedupKey, request, ack);
    }

    private void process(String topic, String dedupKey, NotificationRequest request, Acknowledgment ack) {
        auditLogger.eventReceived(topic, dedupKey);
        try {
            dispatcher.dispatch(request);
            metrics.eventProcessed(topic);
            ack.acknowledge();
        } catch (RuntimeException ex) {
            metrics.eventFailed(topic);
            throw ex;
        }
    }

    /**
     * messageId có sẵn thì dùng nó; không thì rơi xuống khoá nghiệp vụ nếu topic đó có; cuối cùng
     * mới dùng toạ độ bản ghi Kafka — cách này khớp với {@code EventDedupStore} của user-service,
     * và tránh đúng lỗi mà email-service từng mắc: sinh khoá theo {@code System.currentTimeMillis()}
     * khiến mỗi lần nhận lại đều tạo ra một khoá mới, làm chống trùng trở thành vô tác dụng.
     */
    private static String resolveDedupKey(String messageId, String naturalKey,
                                          String topic, Integer partition, Long offset) {
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        if (naturalKey != null && !naturalKey.isBlank()) {
            return naturalKey;
        }
        return topic + ":" + partition + ":" + offset;
    }

    private static Locale resolveLocale(String tag) {
        return (tag == null || tag.isBlank()) ? DEFAULT_LOCALE : Locale.forLanguageTag(tag);
    }
}

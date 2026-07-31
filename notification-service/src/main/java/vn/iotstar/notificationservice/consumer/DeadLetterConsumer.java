package vn.iotstar.notificationservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.service.AuditLogger;
import vn.iotstar.utils.constants.TopicNames;

/**
 * Ghi nhận message rơi vào topic chết.
 * <p>
 * Không có gì trong repo tiêu thụ các topic {@code .DLT} trước khi service này tồn tại — message
 * hỏng nằm lại đó vĩnh viễn mà không ai biết. Ở đây chưa làm redrive tự động (cần con người quyết
 * định message hỏng có nên gửi lại không), chỉ đảm bảo có log có cấu trúc và một counter để gắn
 * cảnh báo Grafana.
 * <p>
 * Dùng {@code dltKafkaListenerContainerFactory} riêng, không có retry và không có
 * {@code DeadLetterPublishingRecoverer}: dùng chung factory với consumer chính sẽ khiến lỗi ở
 * listener này bị đẩy tiếp sang {@code <topic>.DLT.DLT} — không ai đọc, coi như mất luôn.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final NotificationMetrics metrics;
    private final AuditLogger auditLogger;

    @KafkaListener(
            topics = {
                    TopicNames.SEND_EMAIL + TopicNames.DLT_SUFFIX,
                    TopicNames.ACTIVATE_ACCOUNT + TopicNames.DLT_SUFFIX,
                    TopicNames.NOTIFICATION_REQUESTED + TopicNames.DLT_SUFFIX
            },
            groupId = "notification-service-dlt",
            containerFactory = "dltKafkaListenerContainerFactory")
    public void onDeadLetter(Object payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = "kafka_dlt-exception-message", required = false) String exceptionMessage,
            Acknowledgment ack) {

        log.error("Message rơi vào topic chết: topic={} key={} lý do={} payload={}",
                topic, key, exceptionMessage, payload);
        metrics.eventDeadLettered(topic);
        auditLogger.deadLettered(topic, key);
        ack.acknowledge();
    }
}

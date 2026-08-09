package vn.iotstar.notificationservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.service.AuditLogger;
import vn.iotstar.notificationservice.util.TopicNames;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
    public void onDeadLetter(@Payload Object payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) byte[] originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) byte[] originalOffset,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            Acknowledgment ack) {

        // @Payload là bắt buộc: không có nó, Spring Kafka truyền nguyên ConsumerRecord vào tham
        // số kiểu Object, và mỗi message hỏng sinh ra một dòng log vài chục nghìn ký tự vì
        // header kafka_dlt-exception-stacktrace bị in ra dưới dạng mảng byte.
        log.error("Message rơi vào topic chết: topic={} key={} originalTopic={} originalOffset={} lý do={} payload={}",
                topic, key, asString(originalTopic), asLong(originalOffset), exceptionMessage, payload);
        metrics.eventDeadLettered(topic);
        auditLogger.deadLettered(topic, key);
        ack.acknowledge();
    }

    private static String asString(byte[] header) {
        return header == null ? null : new String(header, StandardCharsets.UTF_8);
    }

    /** Kafka ghi các header offset dưới dạng 8 byte big-endian, không phải chuỗi. */
    private static Long asLong(byte[] header) {
        return header == null || header.length != Long.BYTES
                ? null
                : ByteBuffer.wrap(header).getLong();
    }
}

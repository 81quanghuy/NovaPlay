package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.service.EventDedupStore;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.util.TopicName;
import vn.iotstar.userservice.common.dto.MediaReadyEvent;
import vn.iotstar.userservice.common.dto.UserRegister;

/**
 * Consumer Kafka.
 * <p>
 * Các listener ở đây cố tình KHÔNG bắt exception: {@code DefaultErrorHandler} cấu hình trong
 * {@code KafkaConsumerConfig} mới là nơi chịu trách nhiệm retry rồi đẩy message hỏng sang
 * topic {@code .DLT}. Bắt và nuốt exception tại đây sẽ vô hiệu hoá toàn bộ cơ chế đó và
 * biến mọi lỗi xử lý thành mất message vĩnh viễn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl {

    private final UserProfileService userProfileService;
    private final EventDedupStore dedupStore;
    private final UserMetrics metrics;

    @KafkaListener(topics = TopicName.ACTIVATE_ACCOUNT,
            containerFactory = "activateAccountKafkaListenerContainerFactory")
    public void handle(UserRegister evt,
                       @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                       @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
                       @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        validateUserRegister(evt);

        String eventKey = eventKey(topic, partition, offset);
        if (!dedupStore.acquireOnce(eventKey)) {
            metrics.getKafkaEventDuplicate().increment();
            log.info("Skipping already-processed event {} for email {}", eventKey, evt.email());
            return;
        }

        try {
            userProfileService.registerNewUser(evt);
            metrics.getKafkaEventProcessed().increment();
        } catch (Exception ex) {
            // Nhả cọc để lần retry của error handler còn được phép chạy lại.
            dedupStore.release(eventKey);
            metrics.getKafkaEventFailed().increment();
            log.error("Failed to register user from event {} (email={})", eventKey, evt.email(), ex);
            throw ex;
        }
    }

    @KafkaListener(topics = TopicName.SEND_STATUS_MEDIA,
            containerFactory = "mediaReadyKafkaListenerContainerFactory")
    public void handleMediaReadyEvent(MediaReadyEvent event,
                                      @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                      @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
                                      @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        validateMediaReady(event);

        String eventKey = eventKey(topic, partition, offset);
        if (!dedupStore.acquireOnce(eventKey)) {
            metrics.getKafkaEventDuplicate().increment();
            log.info("Skipping already-processed event {} for ownerId {}", eventKey, event.ownerId());
            return;
        }

        try {
            userProfileService.changeUrlAvatar(event.cdnUrl(), event.ownerId());
            metrics.getKafkaEventProcessed().increment();
        } catch (Exception ex) {
            dedupStore.release(eventKey);
            metrics.getKafkaEventFailed().increment();
            log.error("Failed to apply media-ready event {} (ownerId={})", eventKey, event.ownerId(), ex);
            throw ex;
        }
    }

    /** Toạ độ duy nhất của một record trong Kafka. */
    private static String eventKey(String topic, Integer partition, Long offset) {
        return topic + ":" + partition + ":" + offset;
    }

    private void validateUserRegister(UserRegister evt) {
        if (evt == null) throw new IllegalArgumentException("Null payload");
        if (evt.username() == null || evt.username().isBlank())
            throw new IllegalArgumentException("Invalid username");
        if (evt.email() == null || evt.email().isBlank())
            throw new IllegalArgumentException("Invalid email");
    }

    private void validateMediaReady(MediaReadyEvent event) {
        if (event == null) throw new IllegalArgumentException("Null payload");
        if (event.ownerId() == null || event.ownerId().isBlank())
            throw new IllegalArgumentException("Invalid ownerId");
        if (event.cdnUrl() == null || event.cdnUrl().isBlank())
            throw new IllegalArgumentException("Invalid cdnUrl");
    }
}

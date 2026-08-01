package vn.iotstar.promotionservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.promotionservice.model.entity.OutboxEvent;
import vn.iotstar.promotionservice.repository.OutboxEventRepository;
import vn.iotstar.promotionservice.service.EventPublisher;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherImpl implements EventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publish(String topic, String key, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize event payload for topic={}", topic, e);
            throw new RuntimeException("Event serialization failed", e);
        }

        OutboxEvent event = OutboxEvent.builder()
                .topic(topic)
                .key(key)
                .payload(payloadJson)
                .build();
        outboxEventRepository.save(event);
        log.debug("Outbox event saved: topic={}, key={}", topic, key);

        try {
            kafkaTemplate.send(topic, key, payload).get(30, java.util.concurrent.TimeUnit.SECONDS);
            event.setStatus(OutboxEvent.OutboxStatus.SENT);
            event.setSentAt(java.time.Instant.now());
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Kafka send failed, event will be retried by relay job: topic={}, error={}", topic, e.getMessage());
        }
    }
}

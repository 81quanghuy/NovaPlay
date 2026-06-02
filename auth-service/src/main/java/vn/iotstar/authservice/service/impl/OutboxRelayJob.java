package vn.iotstar.authservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.authservice.model.entity.OutboxEvent;
import vn.iotstar.authservice.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setErrorMessage("Max retry attempts exceeded");
                outboxEventRepository.save(event);
                log.error("Outbox event permanently failed after {} attempts: id={}", MAX_ATTEMPTS, event.getId());
                continue;
            }

            try {
                Object payload = objectMapper.readValue(event.getPayload(), Object.class);
                kafkaTemplate.send(event.getTopic(), event.getKey(), payload).get(10, java.util.concurrent.TimeUnit.SECONDS);
                event.setStatus(OutboxEvent.OutboxStatus.SENT);
                event.setSentAt(Instant.now());
                outboxEventRepository.save(event);
                log.debug("Outbox event relayed: id={}, topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setErrorMessage(e.getMessage());
                outboxEventRepository.save(event);
                log.warn("Outbox relay failed for id={}, attempt={}: {}", event.getId(), event.getAttempts(), e.getMessage());
            }
        }
    }
}

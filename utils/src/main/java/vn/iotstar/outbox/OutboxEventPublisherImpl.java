package vn.iotstar.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Ghi ý định gửi vào bảng outbox và dừng ở đó.
 * <p>
 * Lớp này không có collaborator nào liên quan tới Kafka, và đó là điểm mấu chốt: bản cũ
 * ({@code EventPublisherImpl}) gọi {@code kafkaTemplate.send(...).get(30s)} ngay trong transaction
 * nghiệp vụ, nên nếu transaction rollback sau đó thì message đã bay sang Kafka rồi. Việc gửi thật
 * do {@link OutboxRelayService} đảm nhiệm, và chỉ khởi động sau khi Postgres xác nhận đã COMMIT.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final OutboxDao dao;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, String key, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OutboxSerializationException(topic, e);
        }

        UUID id = dao.insert(topic, key, payloadJson);
        log.debug("Đã ghi outbox: id={}, topic={}, key={}", id, topic, key);
    }
}

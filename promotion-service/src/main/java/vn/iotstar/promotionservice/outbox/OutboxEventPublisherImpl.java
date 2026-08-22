package vn.iotstar.promotionservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Ghi ý định gửi vào bảng outbox, rồi đánh thức relay ngay sau khi transaction COMMIT.
 * <p>
 * Lớp này không có collaborator nào liên quan tới Kafka, và đó là điểm mấu chốt: bản cũ
 * ({@code EventPublisherImpl}) gọi {@code kafkaTemplate.send(...).get(30s)} ngay trong transaction
 * nghiệp vụ, nên nếu transaction rollback sau đó thì message đã bay sang Kafka rồi. Việc gửi thật
 * do {@link OutboxRelayService} đảm nhiệm, và chỉ khởi động sau khi Postgres xác nhận đã COMMIT.
 * <p>
 * Việc đánh thức diễn ra TRONG CÙNG JVM. Trước đây nó đi vòng qua Postgres {@code NOTIFY} rồi
 * quay về đúng process này qua một connection {@code LISTEN} — một vòng qua database để tự nói
 * chuyện với chính mình. LISTEN/NOTIFY chỉ đáng giá khi bên ghi row và bên relay là hai process
 * khác nhau; ở đây chúng là một, nên gọi thẳng vừa nhanh hơn vừa không phụ thuộc việc đường tới
 * Postgres có đi qua connection pooler hay không.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final OutboxDao dao;
    private final ObjectMapper objectMapper;
    private final OutboxRelayService relayService;
    private final OutboxProperties properties;
    private final Executor relayExecutor;

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

        if (properties.isRelayAfterCommit()) {
            relayAfterCommit(id);
        }
    }

    /**
     * Chờ COMMIT rồi mới relay. Gửi sớm hơn một nhịp là sai hẳn về mặt nghiệp vụ: transaction có
     * thể rollback sau đó, và Kafka thì không rút message về được — đúng cái bug mà outbox sinh ra
     * để diệt.
     */
    private void relayAfterCommit(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Không có transaction nào đang chạy nghĩa là INSERT ở trên đã tự commit rồi.
            submit(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(id);
            }
        });
    }

    /**
     * Không bao giờ để exception thoát ra ngoài. Ở nhánh chưa có transaction, ném ở đây sẽ rollback
     * cả transaction nghiệp vụ chỉ vì không đánh thức được relay; ở nhánh {@code afterCommit},
     * transaction đã commit nên ném ra cũng chẳng cứu được gì mà chỉ làm rối caller. Cả hai trường
     * hợp row vẫn nằm nguyên trong bảng ở trạng thái PENDING và {@link OutboxSweepJob} sẽ nhặt.
     */
    private void submit(UUID id) {
        try {
            relayExecutor.execute(() -> relayService.relay(id));
        } catch (RejectedExecutionException e) {
            log.warn("Không nộp được lượt relay ngay sau commit cho outbox id={}, để sweep nhặt lại", id, e);
        }
    }
}

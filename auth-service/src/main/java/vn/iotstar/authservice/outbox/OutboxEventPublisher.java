package vn.iotstar.authservice.outbox;

/**
 * Điểm vào duy nhất để service nghiệp vụ phát sự kiện ra ngoài.
 * <p>
 * Gọi trong một method đã có {@code @Transactional}: bản ghi outbox đi theo đúng số phận của
 * transaction đó. Commit thì sự kiện chắc chắn được gửi; rollback thì không có gì lọt ra ngoài.
 */
public interface OutboxEventPublisher {

    /**
     * @param topic tên topic Kafka, lấy từ {@code vn.iotstar.authservice.util.TopicName}
     * @param key   khoá phân vùng, quyết định thứ tự giữa các message cùng khoá
     */
    void publish(String topic, String key, Object payload);
}

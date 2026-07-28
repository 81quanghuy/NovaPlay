package vn.iotstar.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Chống xử lý lặp event Kafka. Kafka giao message theo ngữ nghĩa at-least-once, nên cùng
 * một event có thể tới nhiều lần (rebalance, commit offset lỗi, hoặc producer gửi lại).
 */
@Component
@RequiredArgsConstructor
public class EventDedupStore {

    /** Đủ dài để phủ mọi lần giao lại hợp lý, đủ ngắn để không giữ key vô hạn. */
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private static final String KEY_PREFIX = "user-service:event:";

    private final StringRedisTemplate redis;

    /**
     * Đặt cọc cho một event. Trả về true nếu đây là lần đầu thấy event này.
     * <p>
     * Phải gọi {@link #release} khi xử lý thất bại, nếu không lần retry của Kafka sẽ bị
     * chính cọc này chặn lại và event coi như bị bỏ qua.
     */
    public boolean acquireOnce(String eventKey) {
        Boolean acquired = redis.opsForValue().setIfAbsent(KEY_PREFIX + eventKey, "1", DEFAULT_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String eventKey) {
        redis.delete(KEY_PREFIX + eventKey);
    }
}

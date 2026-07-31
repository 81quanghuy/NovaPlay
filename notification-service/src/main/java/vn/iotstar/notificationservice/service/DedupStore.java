package vn.iotstar.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Khoá chống gửi trùng, đặt trên Redis.
 * <p>
 * Redis dùng chung cho nhiều service nên mọi khoá đều mang tiền tố {@value #KEY_PREFIX}.
 */
@Service
@RequiredArgsConstructor
public class DedupStore {

    private static final String KEY_PREFIX = "notification-service:dedup:";

    private final StringRedisTemplate redis;

    /**
     * Giành quyền xử lý {@code key}. Trả về {@code true} đúng một lần cho tới khi khoá hết hạn
     * hoặc được {@link #release(String)} trả lại.
     */
    public boolean acquireOnce(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", ttl); // SET key "1" NX EX <ttl>
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Trả lại khoá để lần nhận lại thông điệp còn xử lý được.
     * <p>
     * Chỉ gọi khi chắc chắn tác vụ <em>chưa</em> có hiệu lực bên ngoài. Trả khoá sau khi email đã
     * rời khỏi máy chủ SMTP nghĩa là người dùng sẽ nhận thêm một bản nữa ở lần thử kế tiếp.
     */
    public void release(String key) {
        redis.delete(KEY_PREFIX + key);
    }
}

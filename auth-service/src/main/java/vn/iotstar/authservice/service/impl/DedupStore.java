package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DedupStore {

    private final StringRedisTemplate redis;

    /**
     * Đặt "cọc" theo key. Trả về true nếu chưa từng xử lý (đặt mới thành công),
     * false nếu key đã tồn tại (đã/đang xử lý).
     */
    public boolean acquireOnce(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl); // SET key "1" NX EX <ttl>
        return Boolean.TRUE.equals(ok);
    }

    /** Xoá cọc để cho phép xử lý lại (dùng khi side-effect thất bại). */
    public void release(String key) {
        redis.delete(key);
    }
}

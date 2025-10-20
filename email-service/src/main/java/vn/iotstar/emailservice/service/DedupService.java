package vn.iotstar.emailservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DedupService {

    private final StringRedisTemplate redis;

    /**
     * Try to acquire a lock for the given key.
     */
    public boolean acquireOnce(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl); // SET key "1" NX EX <ttl>
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Release the lock for the given key.
     */
    public void release(String key) {
        redis.delete(key);
    }
}

package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.service.RateLimiterService;
import vn.iotstar.utils.exceptions.wrapper.TooManyRequestsException;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

    private final StringRedisTemplate redis;

    @Override
    public void checkAndIncrement(String key, int max, Duration window) throws TooManyRequestsException {
        String luaScript = "local count = redis.call('INCR', KEYS[1])\n" +
                          "if count == 1 then\n" +
                          "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                          "end\n" +
                          "return count";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long count = redis.execute(script, Collections.singletonList(key), String.valueOf(window.getSeconds()));

        if (count != null && count > max) {
            throw new TooManyRequestsException("Too many attempts. Please try again later.");
        }
    }

    @Override
    public void reset(String key) {
        redis.delete(key);
    }

    @Override
    public long getCurrentCount(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

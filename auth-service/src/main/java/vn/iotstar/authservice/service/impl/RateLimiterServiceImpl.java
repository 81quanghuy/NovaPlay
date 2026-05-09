package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.service.RateLimiterService;
import vn.iotstar.utils.exceptions.wrapper.TooManyRequestsException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

    private final StringRedisTemplate redis;

    @Override
    public void checkAndIncrement(String key, int max, Duration window) throws TooManyRequestsException {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
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

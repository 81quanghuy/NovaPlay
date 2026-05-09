package vn.iotstar.authservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.iotstar.utils.exceptions.wrapper.TooManyRequestsException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceImplTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RateLimiterServiceImpl rateLimiter;

    @Test
    void checkAndIncrement_belowLimit_doesNotThrow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(1L);
        assertDoesNotThrow(() ->
                rateLimiter.checkAndIncrement("key", 5, Duration.ofMinutes(15)));
    }

    @Test
    void checkAndIncrement_firstIncrement_setsExpiry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(1L);
        rateLimiter.checkAndIncrement("key", 5, Duration.ofMinutes(15));
        verify(redis).expire(eq("key"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void checkAndIncrement_aboveLimit_throwsTooManyRequests() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(6L);
        assertThrows(TooManyRequestsException.class, () ->
                rateLimiter.checkAndIncrement("key", 5, Duration.ofMinutes(15)));
    }

    @Test
    void checkAndIncrement_atLimit_doesNotThrow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(5L);
        assertDoesNotThrow(() ->
                rateLimiter.checkAndIncrement("key", 5, Duration.ofMinutes(15)));
    }

    @Test
    void reset_deletesKey() {
        rateLimiter.reset("key");
        verify(redis).delete("key");
    }

    @Test
    void getCurrentCount_returnsZeroWhenNull() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        assertEquals(0L, rateLimiter.getCurrentCount("key"));
    }

    @Test
    void getCurrentCount_returnsCount() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn("3");
        assertEquals(3L, rateLimiter.getCurrentCount("key"));
    }
}

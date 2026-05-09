package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.service.TokenBlacklist;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class TokenBlacklistImpl implements TokenBlacklist {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redis;

    @Override
    public void revoke(String jti, Date expiration) {
        long ttlSeconds = expiration.toInstant().getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}

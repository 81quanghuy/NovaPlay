package vn.iotstar.userservice.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import vn.iotstar.userservice.service.UserProfileLookup;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /** Ngắn hơn nhiều so với vòng đời profile, đủ để cắt round-trip lặp trong một phiên xem. */
    private static final Duration USER_ID_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheConfiguration cacheConfiguration(ObjectMapper objectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(USER_ID_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .prefixCacheNameWith("user-service:cache:");
    }

    @Bean
    public org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer cacheManagerCustomizer(
            RedisCacheConfiguration cacheConfiguration) {
        return builder -> builder.withCacheConfiguration(UserProfileLookup.USER_ID_CACHE, cacheConfiguration);
    }
}

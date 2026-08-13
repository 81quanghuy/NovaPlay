package vn.iotstar.streamingservice.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import vn.iotstar.streamingservice.utils.CacheNames;

import java.time.Duration;

@Configuration
public class CacheConfig {

    /**
     * TTL hữu hạn cho cache phân giải manifest — bound độ trễ khi admin re-transcode một phim mà
     * không phải xây một cơ chế evict chủ động phức tạp hơn cho v1. Serialize giá trị bằng JSON
     * (không phải Java serialization mặc định của RedisCacheConfiguration): DTO cache là record,
     * không implement Serializable, và JSON dễ debug trực tiếp trong Redis hơn nhiều.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer manifestResolutionCacheCustomizer() {
        RedisCacheConfiguration manifestConfig = jsonConfig(Duration.ofMinutes(10));
        // TTL ngắn hơn nhiều: admin đổi gói cước phải có hiệu lực gần như ngay, không phải chờ
        // tới khi manifest cache tự hết hạn.
        RedisCacheConfiguration planConfig = jsonConfig(Duration.ofMinutes(5));
        return builder -> builder
                .withCacheConfiguration(CacheNames.MANIFEST_RESOLUTION, manifestConfig)
                .withCacheConfiguration(CacheNames.USER_PLAN, planConfig);
    }

    private RedisCacheConfiguration jsonConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}

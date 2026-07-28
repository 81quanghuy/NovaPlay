package vn.iotstar.movieservice.config.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import vn.iotstar.movieservice.model.dto.GenreDTO;
import vn.iotstar.movieservice.model.dto.MovieDetailDTO;
import vn.iotstar.movieservice.utils.CacheNames;

import java.time.Duration;
import java.util.List;

/**
 * Cache Redis cho phần đọc của catalog — đọc rất nhiều, ghi rất thưa.
 * <p>
 * Cố tình KHÔNG cache danh sách phim phân trang: số tổ hợp bộ lọc × trang × thứ tự sắp xếp quá
 * lớn nên tỉ lệ trúng cache thấp, chỉ tổ chiếm bộ nhớ. Index của MongoDB đã đủ cho truy vấn đó.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer(
            @Value("${application.cache.movie-detail-ttl:10m}") Duration movieDetailTtl,
            @Value("${application.cache.genre-list-ttl:1h}") Duration genreListTtl) {

        ObjectMapper mapper = cacheObjectMapper();
        JavaType genreList = mapper.getTypeFactory()
                .constructCollectionType(List.class, GenreDTO.class);

        return builder -> builder
                .withCacheConfiguration(CacheNames.MOVIE_DETAIL,
                        config(movieDetailTtl,
                                new Jackson2JsonRedisSerializer<>(mapper, MovieDetailDTO.class)))
                .withCacheConfiguration(CacheNames.GENRE_LIST,
                        config(genreListTtl,
                                new Jackson2JsonRedisSerializer<>(mapper, genreList)));
    }

    private RedisCacheConfiguration config(Duration ttl, RedisSerializer<?> valueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                // Không cache null: một lần tra phim không tồn tại sẽ ghim kết quả rỗng lại,
                // che mất phim thật ngay khi admin vừa tạo nó.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                // Tách namespace khỏi user-service, hai service dùng chung một Redis.
                .prefixCacheNameWith("movie-service:cache:");
    }

    /**
     * ObjectMapper riêng cho cache, tách khỏi bean dùng cho HTTP.
     * <p>
     * Mỗi vùng cache được gắn cứng một kiểu giá trị cụ thể thay vì bật default typing của Jackson.
     * Hai lý do:
     * <ul>
     *   <li>Các DTO ở đây là {@code record}, tức lớp final, mà {@code DefaultTyping.NON_FINAL}
     *       không ghi thuộc tính {@code @class} cho lớp final. Giá trị ghi vào được nhưng lúc đọc
     *       ra sẽ ném {@code InvalidTypeIdException: missing type id property '@class'}, biến mọi
     *       lần đọc chi tiết phim thành lỗi 500.</li>
     *   <li>Deserialize đa hình không giới hạn là một lỗ hổng thực thi mã từ xa nếu ai đó ghi được
     *       vào Redis. Gắn cứng kiểu thì rủi ro đó biến mất hoàn toàn.</li>
     * </ul>
     * Không dùng chung ObjectMapper của HTTP vì cấu hình hai nơi phục vụ hai mục đích khác nhau và
     * không nên ràng buộc lẫn nhau.
     */
    private ObjectMapper cacheObjectMapper() {
        // LocalDate trong MovieDetailDTO cần JavaTimeModule, nếu không sẽ bị ghi thành mảng số.
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}

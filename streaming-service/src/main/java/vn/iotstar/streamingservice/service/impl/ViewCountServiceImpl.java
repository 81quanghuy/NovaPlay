package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.iotstar.streamingservice.entity.ViewCounter;
import vn.iotstar.streamingservice.service.ViewCountService;

import java.time.Duration;

/**
 * Dedup qua Redis {@code SET NX EX} rồi {@code $inc} nguyên tử vào Mongo — KHÔNG phải một job flush
 * định kỳ quét theo pattern Redis (KEYS/SCAN theo pattern trên production Redis là anti-pattern,
 * chặn event loop). Vì đã dedup theo cửa sổ thời gian, tần suất ghi Mongo thực tế đã rất thấp
 * (tối đa 1 lần/user/phim/cửa sổ) — không cần thêm một tầng batch nữa cho v1.
 */
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private static final String DEDUP_KEY_PREFIX = "streaming-service:view-dedup:";

    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${streaming.view-dedup-window-minutes:30}")
    private long dedupWindowMinutes;

    @Override
    public void recordView(String userEmail, String movieId) {
        String key = DEDUP_KEY_PREFIX + userEmail + ":" + movieId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(dedupWindowMinutes));
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(movieId)),
                new Update().inc("totalViews", 1),
                ViewCounter.class);
    }
}

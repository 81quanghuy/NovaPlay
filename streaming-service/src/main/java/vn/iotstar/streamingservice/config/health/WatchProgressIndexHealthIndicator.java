package vn.iotstar.streamingservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.entity.WatchProgress;

import java.util.List;

/**
 * Báo DOWN nếu unique index trên {@code (userEmail, movieId, episodeNumber)} không tồn tại.
 * <p>
 * Thiếu index này thì service vẫn nhận request bình thường nhưng âm thầm cho phép hai bản ghi
 * tiến độ xem trùng khoá — vỡ bất biến mà {@code upsertProgress} (tìm-hoặc-tạo) và "continue
 * watching" đều dựa vào. Đưa vào readiness để pod thiếu index không nhận traffic.
 */
@Component
@RequiredArgsConstructor
public class WatchProgressIndexHealthIndicator implements HealthIndicator {

    private static final List<String> UNIQUE_KEYS = List.of("userEmail", "movieId", "episodeNumber");

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            boolean unique = hasUniqueIndexOn(UNIQUE_KEYS);
            if (unique) {
                return Health.up()
                        .withDetail("watchProgressUniqueKey", "unique index present")
                        .build();
            }
            return Health.down()
                    .withDetail("watchProgressUniqueKey", "MISSING")
                    .withDetail("reason", "Required unique index is missing; duplicate watch progress records are possible")
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    /**
     * So khớp theo bộ field chứ không theo tên index: index có thể được tạo bởi
     * {@code auto-index-creation} với tên do nó tự đặt, hoặc bởi
     * {@link vn.iotstar.streamingservice.config.WatchProgressIndexInitializer} với tên của chúng
     * ta. Điều quan trọng là ràng buộc có tồn tại hay không, không phải nó tên gì.
     */
    private boolean hasUniqueIndexOn(List<String> keys) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(WatchProgress.class).getIndexInfo();
        return indexes.stream().anyMatch(i -> i.isUnique()
                && i.getIndexFields().stream().map(IndexField::getKey).toList().equals(keys));
    }
}

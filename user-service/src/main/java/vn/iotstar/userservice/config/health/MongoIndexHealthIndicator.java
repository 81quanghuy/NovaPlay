package vn.iotstar.userservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.model.entity.UserProfile;

import java.util.List;

/**
 * Báo DOWN nếu các unique index mà tính đúng đắn phụ thuộc vào không tồn tại.
 * <p>
 * Thiếu chúng thì service vẫn nhận request bình thường nhưng âm thầm cho phép profile trùng
 * email và mục yêu thích trùng — một dạng hỏng chỉ lộ ra sau khi dữ liệu đã sai. Đưa vào
 * readiness để pod thiếu index không nhận traffic.
 */
@Component
@RequiredArgsConstructor
public class MongoIndexHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            boolean emailUnique = hasUniqueIndex(UserProfile.class, "uk_email");
            boolean favoriteUnique = hasUniqueIndex(FavoriteItem.class, "uk_profile_movie");

            if (emailUnique && favoriteUnique) {
                return Health.up()
                        .withDetail("uk_email", "present")
                        .withDetail("uk_profile_movie", "present")
                        .build();
            }
            return Health.down()
                    .withDetail("uk_email", emailUnique ? "present" : "MISSING")
                    .withDetail("uk_profile_movie", favoriteUnique ? "present" : "MISSING")
                    .withDetail("reason", "Required unique indexes are missing; duplicate records are possible")
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    private boolean hasUniqueIndex(Class<?> entity, String indexName) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entity).getIndexInfo();
        return indexes.stream().anyMatch(i -> indexName.equals(i.getName()) && i.isUnique());
    }
}

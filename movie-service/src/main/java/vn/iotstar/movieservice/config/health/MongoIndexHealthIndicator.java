package vn.iotstar.movieservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.model.entity.Movie;

import java.util.List;

import static vn.iotstar.movieservice.utils.Constants.*;

/**
 * Báo DOWN nếu các unique index mà tính đúng đắn phụ thuộc vào không tồn tại.
 * <p>
 * Thiếu chúng thì service vẫn nhận request bình thường nhưng âm thầm cho phép hai phim chiếm cùng
 * một slug và thể loại trùng tên — một dạng hỏng chỉ lộ ra sau khi dữ liệu đã sai. Đưa vào
 * readiness để pod thiếu index không nhận traffic.
 */
@Component
@RequiredArgsConstructor
public class MongoIndexHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            boolean movieSlugUnique = hasUniqueIndexOn(Movie.class, List.of(SLUG));
            boolean genreNameUnique = hasUniqueIndexOn(Genre.class, List.of(GENRE_NAME));
            boolean genreSlugUnique = hasUniqueIndexOn(Genre.class, List.of(SLUG));

            if (movieSlugUnique && genreNameUnique && genreSlugUnique) {
                return Health.up()
                        .withDetail("movieSlug", "unique index present")
                        .withDetail("genreName", "unique index present")
                        .withDetail("genreSlug", "unique index present")
                        .build();
            }
            return Health.down()
                    .withDetail("movieSlug", movieSlugUnique ? "unique index present" : "MISSING")
                    .withDetail("genreName", genreNameUnique ? "unique index present" : "MISSING")
                    .withDetail("genreSlug", genreSlugUnique ? "unique index present" : "MISSING")
                    .withDetail("reason", "Required unique indexes are missing; duplicate records are possible")
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    /**
     * So khớp theo bộ field chứ không theo tên index: index có thể được tạo bởi
     * auto-index-creation với tên do nó tự đặt, hoặc bởi MongoIndexInitializer với tên của
     * chúng ta. Điều quan trọng là ràng buộc có tồn tại hay không, không phải nó tên gì.
     */
    private boolean hasUniqueIndexOn(Class<?> entity, List<String> keys) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entity).getIndexInfo();
        return indexes.stream().anyMatch(i -> i.isUnique()
                && i.getIndexFields().stream().map(f -> f.getKey()).toList().equals(keys));
    }
}

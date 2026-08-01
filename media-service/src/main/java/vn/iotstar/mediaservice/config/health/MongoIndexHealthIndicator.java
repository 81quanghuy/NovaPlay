package vn.iotstar.mediaservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import vn.iotstar.mediaservice.entity.Media;

import java.util.List;

import static vn.iotstar.mediaservice.util.Constants.MEDIA_S3_KEY;

/**
 * Báo DOWN nếu unique index trên {@code s3Key} không tồn tại.
 * <p>
 * Thiếu index này thì service vẫn nhận request bình thường nhưng âm thầm cho phép hai bản ghi
 * Media trỏ tới cùng một S3 key — vi phạm bất biến "s3Key namespaced theo mediaId là duy nhất"
 * mà cả cache CDN dài hạn lẫn logic tra cứu object đều dựa vào. Đưa vào readiness để pod thiếu
 * index không nhận traffic.
 */
@Component
@RequiredArgsConstructor
public class MongoIndexHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            boolean s3KeyUnique = hasUniqueIndexOn(Media.class, List.of(MEDIA_S3_KEY));

            if (s3KeyUnique) {
                return Health.up()
                        .withDetail("mediaS3Key", "unique index present")
                        .build();
            }
            return Health.down()
                    .withDetail("mediaS3Key", "MISSING")
                    .withDetail("reason", "Required unique index is missing; duplicate S3 keys are possible")
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    /**
     * So khớp theo bộ field chứ không theo tên index: index có thể được tạo bởi
     * auto-index-creation với tên do nó tự đặt, hoặc bởi khai báo {@code @Indexed} với tên của
     * chúng ta. Điều quan trọng là ràng buộc có tồn tại hay không, không phải nó tên gì.
     */
    private boolean hasUniqueIndexOn(Class<?> entity, List<String> keys) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entity).getIndexInfo();
        return indexes.stream().anyMatch(i -> i.isUnique()
                && i.getIndexFields().stream().map(IndexField::getKey).toList().equals(keys));
    }
}

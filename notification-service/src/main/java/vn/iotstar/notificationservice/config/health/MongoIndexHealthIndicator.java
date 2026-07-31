package vn.iotstar.notificationservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.model.entity.Notification;

import java.util.List;

import static vn.iotstar.notificationservice.util.Constants.*;

/**
 * Báo DOWN nếu index mà truy vấn danh sách/đếm chưa đọc phụ thuộc vào không tồn tại.
 * <p>
 * Thiếu chúng service vẫn trả kết quả đúng nhưng full-scan toàn bộ collection cho mỗi request —
 * đưa vào readiness để pod thiếu index không nhận traffic thay vì âm thầm chậm dần khi dữ liệu lớn.
 */
@Component
@RequiredArgsConstructor
public class MongoIndexHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            List<IndexInfo> indexes = mongoTemplate.indexOps(Notification.class).getIndexInfo();
            boolean userCreated = indexes.stream().anyMatch(i -> IDX_USER_CREATED.equals(i.getName()));
            boolean userUnread = indexes.stream().anyMatch(i -> IDX_USER_UNREAD.equals(i.getName()));
            boolean ttl = indexes.stream().anyMatch(i -> IDX_EXPIRES_TTL.equals(i.getName()));

            if (userCreated && userUnread && ttl) {
                return Health.up()
                        .withDetail(IDX_USER_CREATED, "present")
                        .withDetail(IDX_USER_UNREAD, "present")
                        .withDetail(IDX_EXPIRES_TTL, "present")
                        .build();
            }
            return Health.down()
                    .withDetail(IDX_USER_CREATED, userCreated ? "present" : "MISSING")
                    .withDetail(IDX_USER_UNREAD, userUnread ? "present" : "MISSING")
                    .withDetail(IDX_EXPIRES_TTL, ttl ? "present" : "MISSING")
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

package vn.iotstar.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.model.entity.Notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static vn.iotstar.notificationservice.util.Constants.*;

/**
 * Kiểm chứng các index mà tính đúng đắn và hiệu năng của truy vấn in-app phụ thuộc vào.
 * <p>
 * Chỉ dựa vào {@code auto-index-creation} là không đủ: nó chạy lazy theo từng entity và nuốt lỗi
 * im lặng. Chạy lúc khởi động để lỗi lộ ra ngay — thiếu index không làm sai dữ liệu ở đây (không có
 * ràng buộc unique nào ngoài `_id`), nhưng khiến `GET /notifications` full-scan toàn bộ collection.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureUserCreatedIndex();
        ensureUserUnreadIndex();
        ensureExpiresTtlIndex();
        log.info("MongoDB index verification completed for {}", NOTIFICATION_COLLECTION);
    }

    /** Truy vấn danh sách mặc định: lọc theo người dùng, mới nhất trước. */
    private void ensureUserCreatedIndex() {
        ensureIndex(IDX_USER_CREATED, ordered(USER_EMAIL, Sort.Direction.ASC, CREATED_AT, Sort.Direction.DESC),
                null);
    }

    /** Đếm/liệt kê thông báo chưa đọc — partial index chỉ phủ document có readAt=null nên nhẹ hơn
     *  nhiều so với index đầy đủ trên toàn bộ lịch sử thông báo. */
    private void ensureUserUnreadIndex() {
        ensureIndex(IDX_USER_UNREAD,
                ordered(USER_EMAIL, Sort.Direction.ASC, READ_AT, Sort.Direction.ASC,
                        CREATED_AT, Sort.Direction.DESC),
                PartialIndexFilter.of(Criteria.where(READ_AT).is(null)));
    }

    /** Tự dọn thông báo cũ — không cần job xoá riêng. */
    private void ensureExpiresTtlIndex() {
        IndexOperations ops = mongoTemplate.indexOps(Notification.class);
        boolean present = ops.getIndexInfo().stream().anyMatch(info -> IDX_EXPIRES_TTL.equals(info.getName()));
        if (present) {
            log.info("Index {} on {} already present", IDX_EXPIRES_TTL, NOTIFICATION_COLLECTION);
            return;
        }
        ops.createIndex(new Index().named(IDX_EXPIRES_TTL).on(EXPIRES_AT, Sort.Direction.ASC).expire(0));
        log.info("Created TTL index {} on {}", IDX_EXPIRES_TTL, NOTIFICATION_COLLECTION);
    }

    /**
     * Tạo index nếu chưa có index nào phủ đúng bộ field đó.
     * <p>
     * MongoDB từ chối việc tạo cùng một bộ key dưới một cái tên khác (lỗi 85 IndexOptionsConflict).
     * Vì vậy phải so khớp theo bộ key chứ không theo tên, nếu không mọi lần khởi động thứ hai đều
     * thất bại.
     */
    private void ensureIndex(String name, Map<String, Sort.Direction> keys, PartialIndexFilter partial) {
        IndexOperations ops = mongoTemplate.indexOps(Notification.class);
        List<IndexInfo> existing = ops.getIndexInfo();

        boolean present = existing.stream().anyMatch(info -> coversSameKeys(info, keys));
        if (present) {
            log.info("Index covering {} on {} already present", keys.keySet(), NOTIFICATION_COLLECTION);
            return;
        }

        Index index = new Index().named(name);
        keys.forEach(index::on);
        if (partial != null) {
            index.partial(partial);
        }
        ops.createIndex(index);
        log.info("Created index {} on {}", name, NOTIFICATION_COLLECTION);
    }

    private static boolean coversSameKeys(IndexInfo info, Map<String, Sort.Direction> keys) {
        List<String> actual = info.getIndexFields().stream().map(f -> f.getKey()).toList();
        return actual.equals(List.copyOf(keys.keySet()));
    }

    private static Map<String, Sort.Direction> ordered(String k1, Sort.Direction d1, String k2, Sort.Direction d2) {
        Map<String, Sort.Direction> keys = new LinkedHashMap<>();
        keys.put(k1, d1);
        keys.put(k2, d2);
        return keys;
    }

    private static Map<String, Sort.Direction> ordered(String k1, Sort.Direction d1, String k2, Sort.Direction d2,
                                                        String k3, Sort.Direction d3) {
        Map<String, Sort.Direction> keys = ordered(k1, d1, k2, d2);
        keys.put(k3, d3);
        return keys;
    }
}

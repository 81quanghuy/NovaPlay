package vn.iotstar.mediaservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import vn.iotstar.mediaservice.entity.Media;

import java.util.List;
import java.util.Map;

import static vn.iotstar.mediaservice.util.Constants.IDX_MEDIA_OWNER_ID;
import static vn.iotstar.mediaservice.util.Constants.IDX_MEDIA_S3_KEY;
import static vn.iotstar.mediaservice.util.Constants.MEDIA_COLLECTION;
import static vn.iotstar.mediaservice.util.Constants.MEDIA_OWNER_ID;
import static vn.iotstar.mediaservice.util.Constants.MEDIA_S3_KEY;

/**
 * Kiểm chứng các index mà tính đúng đắn và hiệu năng của nghiệp vụ phụ thuộc vào.
 * <p>
 * Chỉ dựa vào {@code auto-index-creation} là không đủ: nó chạy lazy theo từng entity và nuốt lỗi
 * im lặng, nên nếu unique index trên {@code s3Key} không tồn tại thì hai bản ghi Media khác nhau
 * có thể trỏ cùng một S3 key mà không có dấu hiệu nào. Chạy lúc khởi động để lỗi lộ ra ngay thay
 * vì phát hiện sau khi dữ liệu đã sai — và đây cũng chính là index mà
 * {@link vn.iotstar.mediaservice.config.health.MongoIndexHealthIndicator} kiểm tra ở readiness,
 * nên thiếu bước này thì pod sẽ không bao giờ Ready trên một MongoDB mới tinh.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureMediaIndexes();
        log.info("MongoDB index verification completed");
    }

    private void ensureMediaIndexes() {
        ensureIndex(Media.class, MEDIA_COLLECTION, IDX_MEDIA_S3_KEY, true,
                Map.of(MEDIA_S3_KEY, Sort.Direction.ASC));

        // Không unique: chỉ phục vụ truy vấn "media của một owner", đã khai báo sẵn qua
        // @CompoundIndex trên Media.
        ensureIndex(Media.class, MEDIA_COLLECTION, IDX_MEDIA_OWNER_ID, false,
                Map.of(MEDIA_OWNER_ID, Sort.Direction.ASC));
    }

    /**
     * Tạo index nếu chưa có index nào phủ đúng bộ field đó.
     * <p>
     * MongoDB từ chối việc tạo cùng một bộ key dưới một cái tên khác (lỗi 85 IndexOptionsConflict),
     * mà {@code auto-index-creation} thì đã tự tạo index từ {@code @Indexed}/{@code @CompoundIndex}
     * với tên do nó tự đặt. Vì vậy phải so khớp theo bộ key chứ không theo tên, nếu không mọi lần
     * khởi động đều thất bại.
     */
    private void ensureIndex(Class<?> entity, String collection, String name, boolean unique,
                             Map<String, Sort.Direction> keys) {
        IndexOperations ops = mongoTemplate.indexOps(entity);
        List<IndexInfo> existing = ops.getIndexInfo();

        IndexInfo match = existing.stream()
                .filter(info -> coversSameKeys(info, keys))
                .findFirst()
                .orElse(null);

        if (match != null) {
            if (unique && !match.isUnique()) {
                // Nghiêm trọng: index tồn tại nhưng KHÔNG unique, nên nó không hề ngăn dữ liệu
                // trùng. Phải xoá và tạo lại — việc này thất bại nếu dữ liệu trùng đã có sẵn,
                // và đó là hành vi mong muốn: cần con người xử lý dữ liệu trước.
                log.warn("Index {} on {} exists but is not unique, recreating it",
                        match.getName(), collection);
                ops.dropIndex(match.getName());
            } else {
                log.info("Index {} on {} already present (unique={})",
                        match.getName(), collection, match.isUnique());
                return;
            }
        }

        Index index = new Index().named(name);
        keys.forEach(index::on);
        if (unique) {
            index.unique();
        }
        ops.createIndex(index);
        log.info("Created index {} on {} (unique={})", name, collection, unique);
    }

    private static boolean coversSameKeys(IndexInfo info, Map<String, Sort.Direction> keys) {
        List<String> actual = info.getIndexFields().stream().map(f -> f.getKey()).toList();
        return actual.equals(List.copyOf(keys.keySet()));
    }
}

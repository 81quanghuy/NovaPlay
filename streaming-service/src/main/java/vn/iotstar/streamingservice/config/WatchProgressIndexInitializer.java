package vn.iotstar.streamingservice.config;

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
import vn.iotstar.streamingservice.entity.WatchProgress;

import java.util.List;
import java.util.Map;

import static vn.iotstar.streamingservice.utils.Constants.IDX_WATCH_PROGRESS_UNIQUE;
import static vn.iotstar.streamingservice.utils.Constants.WATCH_PROGRESS_COLLECTION;

/**
 * Kiểm chứng unique index trên {@code (userEmail, movieId, episodeNumber)} mà tính đúng đắn của
 * "continue watching" phụ thuộc vào.
 * <p>
 * Chỉ dựa vào {@code auto-index-creation} là không đủ: nó chạy lazy theo từng entity và nuốt lỗi
 * im lặng, nên nếu index này không tồn tại thì một user có thể có hai bản ghi tiến độ xem cho cùng
 * một phim/tập mà không có dấu hiệu nào — vỡ bất biến mà {@code upsertProgress} dựa vào (tìm-hoặc-
 * tạo theo đúng bộ khoá này). Chạy lúc khởi động để lỗi lộ ra ngay, cùng pattern với media-service's
 * {@code MongoIndexInitializer}. Đây cũng là index mà
 * {@link vn.iotstar.streamingservice.config.health.WatchProgressIndexHealthIndicator} kiểm tra ở
 * readiness.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WatchProgressIndexInitializer implements ApplicationRunner {

    private static final Map<String, Sort.Direction> UNIQUE_KEYS = Map.of(
            "userEmail", Sort.Direction.ASC,
            "movieId", Sort.Direction.ASC,
            "episodeNumber", Sort.Direction.ASC);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureWatchProgressIndex();
        log.info("MongoDB index verification completed");
    }

    private void ensureWatchProgressIndex() {
        IndexOperations ops = mongoTemplate.indexOps(WatchProgress.class);
        List<IndexInfo> existing = ops.getIndexInfo();

        IndexInfo match = existing.stream()
                .filter(this::coversUniqueKeys)
                .findFirst()
                .orElse(null);

        if (match != null) {
            if (!match.isUnique()) {
                // Nghiêm trọng: index tồn tại nhưng KHÔNG unique, nên nó không hề ngăn dữ liệu
                // trùng. Phải xoá và tạo lại — việc này thất bại nếu dữ liệu trùng đã có sẵn, và đó
                // là hành vi mong muốn: cần con người xử lý dữ liệu trước.
                log.warn("Index {} on {} exists but is not unique, recreating it",
                        match.getName(), WATCH_PROGRESS_COLLECTION);
                ops.dropIndex(match.getName());
            } else {
                log.info("Index {} on {} already present (unique=true)",
                        match.getName(), WATCH_PROGRESS_COLLECTION);
                return;
            }
        }

        Index index = new Index().named(IDX_WATCH_PROGRESS_UNIQUE).unique();
        UNIQUE_KEYS.forEach(index::on);
        ops.createIndex(index);
        log.info("Created index {} on {}", IDX_WATCH_PROGRESS_UNIQUE, WATCH_PROGRESS_COLLECTION);
    }

    /**
     * So khớp theo bộ key chứ không theo tên: {@code auto-index-creation} có thể đã tự tạo index
     * từ {@code @CompoundIndex} với tên do nó tự đặt trước khi initializer này chạy. MongoDB từ
     * chối tạo cùng một bộ key dưới tên khác (lỗi 85 IndexOptionsConflict).
     */
    private boolean coversUniqueKeys(IndexInfo info) {
        List<String> actual = info.getIndexFields().stream().map(f -> f.getKey()).toList();
        return actual.equals(List.copyOf(UNIQUE_KEYS.keySet()));
    }
}

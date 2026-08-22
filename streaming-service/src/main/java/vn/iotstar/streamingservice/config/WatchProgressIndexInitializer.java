package vn.iotstar.streamingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.entity.WatchProgress;

import java.util.List;

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

    /**
     * THỨ TỰ FIELD LÀ MỘT PHẦN CỦA ĐỊNH DANH INDEX trong MongoDB, phải khớp đúng thứ tự khai ở
     * {@code @CompoundIndex} của {@link WatchProgress} và ở
     * {@link vn.iotstar.streamingservice.config.health.WatchProgressIndexHealthIndicator}.
     * <p>
     * Cố tình dùng {@code List} chứ KHÔNG phải {@code Map.of}: thứ tự duyệt của {@code Map.of}
     * được xáo bằng một salt sinh ngẫu nhiên cho mỗi lần chạy JVM, nên nó vừa tạo index sai thứ
     * tự khoá (compound index chỉ phục vụ query theo tiền tố — sai thứ tự là mất index cho query
     * theo {@code userEmail} mà "continue watching" dùng), vừa làm phép so khớp bên dưới trượt
     * ngẫu nhiên. Hệ quả từng gặp: khởi động lần sau MongoDB trả lỗi 86 IndexKeySpecsConflict
     * (cùng tên index, khác bộ khoá) và service chết ngay lúc boot.
     */
    private static final List<String> UNIQUE_KEYS = List.of("userEmail", "movieId", "episodeNumber");

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
        } else {
            dropConflictingIndexWithOurName(ops, existing);
        }

        Index index = new Index().named(IDX_WATCH_PROGRESS_UNIQUE).unique();
        UNIQUE_KEYS.forEach(key -> index.on(key, Sort.Direction.ASC));
        ops.createIndex(index);
        log.info("Created index {} on {}", IDX_WATCH_PROGRESS_UNIQUE, WATCH_PROGRESS_COLLECTION);
    }

    /**
     * Dọn index mang ĐÚNG tên của chúng ta nhưng bộ khoá lại khác (vd do một bản build cũ tạo sai
     * thứ tự field). MongoDB từ chối tạo đè bằng lỗi 86 IndexKeySpecsConflict, và vì initializer này
     * chạy lúc boot nên hệ quả là pod không bao giờ start được, phải vào sửa tay trên DB. Tên
     * index là do service này đặt và dành riêng, nên xoá đi là an toàn; tạo lại vẫn fail to nếu
     * dữ liệu đang trùng khoá — đúng như mong muốn.
     */
    private void dropConflictingIndexWithOurName(IndexOperations ops, List<IndexInfo> existing) {
        boolean nameTaken = existing.stream()
                .anyMatch(i -> IDX_WATCH_PROGRESS_UNIQUE.equals(i.getName()));
        if (nameTaken) {
            log.warn("Index {} on {} exists with a different key set, dropping it before recreating",
                    IDX_WATCH_PROGRESS_UNIQUE, WATCH_PROGRESS_COLLECTION);
            ops.dropIndex(IDX_WATCH_PROGRESS_UNIQUE);
        }
    }

    /**
     * So khớp theo bộ key (đúng thứ tự) chứ không theo tên: {@code auto-index-creation} có thể đã
     * tự tạo index từ {@code @CompoundIndex} với tên do nó tự đặt trước khi initializer này chạy.
     * MongoDB từ chối tạo cùng một bộ key dưới tên khác (lỗi 85 IndexOptionsConflict).
     */
    private boolean coversUniqueKeys(IndexInfo info) {
        List<String> actual = info.getIndexFields().stream().map(IndexField::getKey).toList();
        return actual.equals(UNIQUE_KEYS);
    }
}

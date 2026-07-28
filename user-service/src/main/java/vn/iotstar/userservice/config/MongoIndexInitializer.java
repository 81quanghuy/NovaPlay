package vn.iotstar.userservice.config;

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
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.model.entity.WatchProgress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static vn.iotstar.userservice.util.Constants.*;

/**
 * Kiểm chứng các index mà tính đúng đắn của nghiệp vụ phụ thuộc vào.
 * <p>
 * Chỉ dựa vào {@code auto-index-creation} là không đủ: nó chạy lazy theo từng entity và nuốt
 * lỗi im lặng, nên nếu unique index trên email hoặc {@code uk_profile_movie} không tồn tại thì
 * cơ chế chống trùng của việc đăng ký user và thêm phim yêu thích sẽ hỏng mà không có dấu hiệu
 * nào. Chạy lúc khởi động để lỗi lộ ra ngay thay vì phát hiện sau khi dữ liệu đã sai.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureUserProfileIndexes();
        ensureFavoriteItemIndexes();
        ensureWatchProgressIndexes();
        log.info("MongoDB index verification completed");
    }

    private void ensureUserProfileIndexes() {
        ensureIndex(UserProfile.class, USER_PROFILE_TABLE_NAME, "uk_email", true,
                Map.of(EMAIL_COLUMN, Sort.Direction.ASC));
    }

    private void ensureFavoriteItemIndexes() {
        ensureIndex(FavoriteItem.class, FAVORITE_ITEM_TABLE_NAME, "uk_profile_movie", true,
                ordered(FAVORITE_ITEM_USER_ID_COLUMN, Sort.Direction.ASC,
                        FAVORITE_ITEM_MOVIE_ID_COLUMN, Sort.Direction.ASC));
    }

    private void ensureWatchProgressIndexes() {
        ensureIndex(WatchProgress.class, WATCH_PROGRESS_TABLE_NAME, "uk_user_movie", true,
                ordered(WATCH_PROGRESS_USER_ID_COLUMN, Sort.Direction.ASC,
                        WATCH_PROGRESS_MOVIE_ID_COLUMN, Sort.Direction.ASC));
        ensureIndex(WatchProgress.class, WATCH_PROGRESS_TABLE_NAME, "idx_user_lastwatched", false,
                ordered(WATCH_PROGRESS_USER_ID_COLUMN, Sort.Direction.ASC,
                        WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN, Sort.Direction.DESC));
    }

    /**
     * Tạo index nếu chưa có index nào phủ đúng bộ field đó.
     * <p>
     * MongoDB từ chối việc tạo cùng một bộ key dưới một cái tên khác (lỗi 85
     * IndexOptionsConflict), mà {@code auto-index-creation} thì đã tự tạo index từ {@code @Indexed}
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

    /** LinkedHashMap để giữ thứ tự field — thứ tự quyết định index có dùng được hay không. */
    private static Map<String, Sort.Direction> ordered(String k1, Sort.Direction d1,
                                                       String k2, Sort.Direction d2) {
        Map<String, Sort.Direction> keys = new LinkedHashMap<>();
        keys.put(k1, d1);
        keys.put(k2, d2);
        return keys;
    }
}

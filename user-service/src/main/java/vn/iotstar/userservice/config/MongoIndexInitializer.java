package vn.iotstar.userservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.model.entity.WatchProgress;

import static vn.iotstar.userservice.util.Constants.*;

/**
 * Tạo tường minh các index mà tính đúng đắn của nghiệp vụ phụ thuộc vào.
 * <p>
 * {@code auto-index-creation} tạo index một cách lazy theo từng entity và nuốt lỗi im lặng,
 * nên chỉ dựa vào nó là không đủ: nếu unique index trên email hoặc {@code uk_profile_movie}
 * không tồn tại thì cơ chế chống trùng của việc đăng ký user và thêm phim yêu thích
 * sẽ hỏng mà không có dấu hiệu nào. Chạy lúc khởi động để lỗi lộ ra ngay.
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
        IndexOperations ops = mongoTemplate.indexOps(UserProfile.class);
        ops.createIndex(new Index().on(EMAIL_COLUMN, Sort.Direction.ASC).unique().named("uk_email"));
        log.info("Ensured index uk_email on {}", USER_PROFILE_TABLE_NAME);
    }

    private void ensureFavoriteItemIndexes() {
        IndexOperations ops = mongoTemplate.indexOps(FavoriteItem.class);
        ops.createIndex(new Index()
                .on(FAVORITE_ITEM_USER_ID_COLUMN, Sort.Direction.ASC)
                .on(FAVORITE_ITEM_MOVIE_ID_COLUMN, Sort.Direction.ASC)
                .unique().named("uk_profile_movie"));
        log.info("Ensured index uk_profile_movie on {}", FAVORITE_ITEM_TABLE_NAME);
    }

    private void ensureWatchProgressIndexes() {
        IndexOperations ops = mongoTemplate.indexOps(WatchProgress.class);
        ops.createIndex(new Index()
                .on(WATCH_PROGRESS_USER_ID_COLUMN, Sort.Direction.ASC)
                .on(WATCH_PROGRESS_MOVIE_ID_COLUMN, Sort.Direction.ASC)
                .unique().named("uk_user_movie"));
        ops.createIndex(new Index()
                .on(WATCH_PROGRESS_USER_ID_COLUMN, Sort.Direction.ASC)
                .on(WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN, Sort.Direction.DESC)
                .named("idx_user_lastwatched"));
        log.info("Ensured indexes uk_user_movie, idx_user_lastwatched on {}", WATCH_PROGRESS_TABLE_NAME);
    }
}

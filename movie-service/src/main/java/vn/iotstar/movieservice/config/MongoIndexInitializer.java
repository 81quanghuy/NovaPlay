package vn.iotstar.movieservice.config;

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
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;
import vn.iotstar.movieservice.model.entity.Artist;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.model.entity.Movie;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static vn.iotstar.movieservice.utils.Constants.*;

/**
 * Kiểm chứng các index mà tính đúng đắn và hiệu năng của nghiệp vụ phụ thuộc vào.
 * <p>
 * Chỉ dựa vào {@code auto-index-creation} là không đủ: nó chạy lazy theo từng entity và nuốt lỗi
 * im lặng, nên nếu unique index trên slug không tồn tại thì hai phim khác nhau có thể chiếm cùng
 * một URL mà không có dấu hiệu nào. Chạy lúc khởi động để lỗi lộ ra ngay thay vì phát hiện sau
 * khi dữ liệu đã sai.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureMovieIndexes();
        ensureGenreIndexes();
        ensureArtistIndexes();
        log.info("MongoDB index verification completed");
    }

    private void ensureMovieIndexes() {
        ensureIndex(Movie.class, MOVIE_COLLECTION, IDX_MOVIE_SLUG, true,
                Map.of(SLUG, Sort.Direction.ASC));

        // Truy vấn duyệt catalog mặc định: lọc theo status rồi sắp xếp theo ngày phát hành giảm dần.
        ensureIndex(Movie.class, MOVIE_COLLECTION, IDX_MOVIE_STATUS_RELEASE, false,
                ordered(MOVIE_STATUS, Sort.Direction.ASC,
                        MOVIE_RELEASE_DATE, Sort.Direction.DESC));

        // Index multikey trên mảng lồng nhau, phục vụ lọc theo thể loại.
        ensureIndex(Movie.class, MOVIE_COLLECTION, IDX_MOVIE_GENRE, false,
                ordered(MOVIE_STATUS, Sort.Direction.ASC,
                        MOVIE_GENRES_ID, Sort.Direction.ASC));

        ensureMovieTitleTextIndex();
    }

    private void ensureGenreIndexes() {
        ensureIndex(Genre.class, GENRE_COLLECTION, IDX_GENRE_NAME, true,
                Map.of(GENRE_NAME, Sort.Direction.ASC));
        ensureIndex(Genre.class, GENRE_COLLECTION, IDX_GENRE_SLUG, true,
                Map.of(SLUG, Sort.Direction.ASC));
    }

    private void ensureArtistIndexes() {
        ensureIndex(Artist.class, ARTIST_COLLECTION, IDX_ARTIST_FULL_NAME, false,
                Map.of(ARTIST_FULL_NAME, Sort.Direction.ASC));
    }

    /**
     * Index text phục vụ tìm kiếm theo tiêu đề.
     * <p>
     * Tách riêng khỏi {@link #ensureIndex}: MongoDB chỉ cho phép đúng MỘT text index trên mỗi
     * collection, nên phải nhận diện theo "đã có text index nào chưa" chứ không theo bộ khoá.
     * <p>
     * Nhận diện bằng {@link org.springframework.data.mongodb.core.index.IndexField#isText()}.
     * MongoDB lưu text index dưới dạng khoá {@code _fts}/{@code _ftsx}, nhưng Spring Data đọc
     * document {@code weights} rồi dựng lại thành các IndexField mang đúng tên field gốc — nên
     * dò theo khoá {@code _fts} sẽ không bao giờ khớp và index bị tạo lại mỗi lần khởi động.
     */
    private void ensureMovieTitleTextIndex() {
        IndexOperations ops = mongoTemplate.indexOps(Movie.class);
        boolean present = ops.getIndexInfo().stream()
                .anyMatch(info -> info.getIndexFields().stream().anyMatch(IndexField::isText));

        if (present) {
            log.info("Text index on {} already present", MOVIE_COLLECTION);
            return;
        }

        ops.createIndex(TextIndexDefinition.builder()
                .named(IDX_MOVIE_TITLE_TEXT)
                .onField(MOVIE_TITLE)
                .build());
        log.info("Created text index {} on {}", IDX_MOVIE_TITLE_TEXT, MOVIE_COLLECTION);
    }

    /**
     * Tạo index nếu chưa có index nào phủ đúng bộ field đó.
     * <p>
     * MongoDB từ chối việc tạo cùng một bộ key dưới một cái tên khác (lỗi 85 IndexOptionsConflict),
     * mà {@code auto-index-creation} thì đã tự tạo index từ {@code @Indexed} với tên do nó tự đặt.
     * Vì vậy phải so khớp theo bộ key chứ không theo tên, nếu không mọi lần khởi động đều thất bại.
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

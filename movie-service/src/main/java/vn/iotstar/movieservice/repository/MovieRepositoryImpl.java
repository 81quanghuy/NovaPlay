package vn.iotstar.movieservice.repository;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import vn.iotstar.movieservice.model.entity.Movie;

import java.util.List;

import static vn.iotstar.movieservice.utils.Constants.*;

/**
 * Phần hiện thực tuỳ biến của {@link MovieRepository}.
 * <p>
 * Bộ lọc duyệt catalog có nhiều tổ hợp tuỳ chọn (trạng thái, thể loại, phim bộ/lẻ, từ khoá), mà
 * derived query của Spring Data thì phải sinh một phương thức cho mỗi tổ hợp. Dựng Criteria
 * động ở đây gọn hơn hẳn.
 */
@RequiredArgsConstructor
public class MovieRepositoryImpl implements MovieRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Movie> search(MovieSearchCriteria criteria, Pageable pageable) {
        Query query = new Query();

        if (criteria.status() != null) {
            query.addCriteria(Criteria.where(MOVIE_STATUS).is(criteria.status()));
        }
        if (criteria.genreId() != null && !criteria.genreId().isBlank()) {
            query.addCriteria(Criteria.where(MOVIE_GENRES_ID).is(criteria.genreId()));
        }
        if (criteria.series() != null) {
            query.addCriteria(Criteria.where(MOVIE_IS_SERIES).is(criteria.series()));
        }
        if (criteria.q() != null && !criteria.q().isBlank()) {
            // Dùng text index thay vì regex: regex không neo đầu chuỗi sẽ quét toàn bộ collection.
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(criteria.q().trim()));
        }

        // Đếm trước khi gắn phân trang: skip/limit sẽ làm count trả về sai số lượng.
        long total = mongoTemplate.count(query, Movie.class);

        List<Movie> content = total == 0
                ? List.of()
                : mongoTemplate.find(query.with(pageable), Movie.class);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long renameEmbeddedGenre(String genreId, String newName) {
        // Toán tử vị trí $ sửa đúng phần tử mảng đã khớp ở điều kiện lọc.
        return mongoTemplate.updateMulti(
                new Query(Criteria.where(MOVIE_GENRES_ID).is(genreId)),
                new Update().set(MOVIE_GENRES + ".$." + GENRE_REF_NAME, newName),
                Movie.class
        ).getModifiedCount();
    }

    @Override
    public long removeEmbeddedGenre(String genreId) {
        return mongoTemplate.updateMulti(
                new Query(Criteria.where(MOVIE_GENRES_ID).is(genreId)),
                new Update().pull(MOVIE_GENRES, new Document(GENRE_REF_ID, genreId)),
                Movie.class
        ).getModifiedCount();
    }

    @Override
    public long renameEmbeddedArtist(String artistId, String newFullName) {
        String castArtistIdPath = MOVIE_CAST + "." + CAST_ARTIST_ID;

        // Một nghệ sĩ có thể xuất hiện nhiều lần trong cùng một phim (vừa đạo diễn vừa diễn viên),
        // mà toán tử $ chỉ sửa phần tử khớp ĐẦU TIÊN. Dùng arrayFilters để sửa mọi phần tử khớp.
        Update update = new Update()
                .set(MOVIE_CAST + ".$[elem]." + CAST_FULL_NAME, newFullName)
                .filterArray(Criteria.where("elem." + CAST_ARTIST_ID).is(artistId));

        return mongoTemplate.updateMulti(
                new Query(Criteria.where(castArtistIdPath).is(artistId)),
                update,
                Movie.class
        ).getModifiedCount();
    }
}

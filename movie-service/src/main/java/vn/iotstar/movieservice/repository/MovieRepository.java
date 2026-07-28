package vn.iotstar.movieservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.movieservice.model.entity.Movie;

import java.util.Optional;

@Repository
public interface MovieRepository extends MongoRepository<Movie, String>, MovieRepositoryCustom {

    Optional<Movie> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Dùng khi sửa phim: slug mới không được trùng với phim KHÁC. */
    boolean existsBySlugAndIdNot(String slug, String id);

    /** Chặn xoá thể loại/nghệ sĩ đang được phim sử dụng. */
    boolean existsByGenresId(String genreId);

    boolean existsByCastArtistId(String artistId);
}

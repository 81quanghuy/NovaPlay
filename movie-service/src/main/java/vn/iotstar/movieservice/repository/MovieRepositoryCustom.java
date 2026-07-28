package vn.iotstar.movieservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.movieservice.model.entity.Movie;

public interface MovieRepositoryCustom {

    Page<Movie> search(MovieSearchCriteria criteria, Pageable pageable);

    /**
     * Cập nhật tên thể loại đã nhúng trong mọi phim đang tham chiếu tới nó.
     *
     * @return số phim đã được sửa
     */
    long renameEmbeddedGenre(String genreId, String newName);

    /** Gỡ thể loại đã bị xoá ra khỏi mọi phim đang tham chiếu. */
    long removeEmbeddedGenre(String genreId);

    /** Cập nhật tên nghệ sĩ đã nhúng trong danh sách ê-kíp của mọi phim liên quan. */
    long renameEmbeddedArtist(String artistId, String newFullName);
}

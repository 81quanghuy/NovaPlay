package vn.iotstar.movieservice.service;

import org.springframework.data.domain.Pageable;
import vn.iotstar.movieservice.model.dto.*;
import vn.iotstar.movieservice.model.enums.MovieStatus;

import java.util.List;

public interface MovieService {

    /** Duyệt catalog công khai: chỉ trả phim đã PUBLISHED. */
    PageResponse<MovieSummaryDTO> browsePublished(String genreId, Boolean series, String q,
                                                  Pageable pageable);

    /** Danh sách cho admin: trả mọi trạng thái, kể cả DRAFT. */
    PageResponse<MovieSummaryDTO> searchAll(MovieStatus status, String genreId, Boolean series,
                                            String q, Pageable pageable);

    /** Chi tiết công khai; ném ResourceNotFoundException nếu phim chưa PUBLISHED. */
    MovieDetailDTO findPublishedByIdOrSlug(String idOrSlug);

    MovieDetailDTO findByIdForAdmin(String id);

    MovieDetailDTO create(MovieRequest request);

    MovieDetailDTO update(String id, MovieRequest request);

    MovieDetailDTO changeStatus(String id, MovieStatus status);

    MovieDetailDTO replaceEpisodes(String id, List<EpisodeRequest> episodes);

    void delete(String id);
}

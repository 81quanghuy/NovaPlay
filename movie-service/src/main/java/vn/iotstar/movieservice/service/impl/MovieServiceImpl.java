package vn.iotstar.movieservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.iotstar.movieservice.mapper.MovieMapper;
import vn.iotstar.movieservice.model.dto.*;
import vn.iotstar.movieservice.model.entity.Artist;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.model.entity.Movie;
import vn.iotstar.movieservice.model.entity.embedded.CastMember;
import vn.iotstar.movieservice.model.entity.embedded.Episode;
import vn.iotstar.movieservice.model.entity.embedded.GenreRef;
import vn.iotstar.movieservice.model.enums.MovieStatus;
import vn.iotstar.movieservice.repository.*;
import vn.iotstar.movieservice.service.MovieService;
import vn.iotstar.movieservice.utils.CacheNames;
import vn.iotstar.movieservice.utils.SlugUtils;
import vn.iotstar.movieservice.exception.BadRequestException;
import vn.iotstar.movieservice.exception.ResourceNotFoundException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final ArtistRepository artistRepository;
    private final MovieMapper mapper;

    // ---------- Đọc ----------

    @Override
    public PageResponse<MovieSummaryDTO> browsePublished(String genreId, Boolean series, String q,
                                                         Pageable pageable) {
        Page<Movie> page = movieRepository.search(
                new MovieSearchCriteria(MovieStatus.PUBLISHED, genreId, series, q), pageable);
        return PageResponse.from(page, mapper::toSummary);
    }

    @Override
    public PageResponse<MovieSummaryDTO> searchAll(MovieStatus status, String genreId,
                                                   Boolean series, String q, Pageable pageable) {
        Page<Movie> page = movieRepository.search(
                new MovieSearchCriteria(status, genreId, series, q), pageable);
        return PageResponse.from(page, mapper::toSummary);
    }

    @Override
    @Cacheable(value = CacheNames.MOVIE_DETAIL, key = "#idOrSlug")
    public MovieDetailDTO findPublishedByIdOrSlug(String idOrSlug) {
        Movie movie = movieRepository.findById(idOrSlug)
                .or(() -> movieRepository.findBySlug(idOrSlug))
                .filter(m -> m.getStatus() == MovieStatus.PUBLISHED)
                // Trả 404 chứ không phải 403 cho phim chưa phát hành: nói "phim này tồn tại nhưng
                // bạn không được xem" là đã tiết lộ kế hoạch nội dung chưa công bố.
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim: " + idOrSlug));
        return mapper.toDetail(movie);
    }

    @Override
    public MovieDetailDTO findByIdForAdmin(String id) {
        return mapper.toDetail(getOrThrow(id));
    }

    // ---------- Ghi ----------

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public MovieDetailDTO create(MovieRequest request) {
        validateSeriesConsistency(request);

        Movie movie = Movie.builder()
                .id(UUID.randomUUID().toString())
                .title(request.title())
                .description(request.description())
                .releaseDate(request.releaseDate())
                .durationInMinutes(request.durationInMinutes())
                .posterUrl(request.posterUrl())
                .isSeries(Boolean.TRUE.equals(request.series()))
                .status(MovieStatus.DRAFT)
                .genres(resolveGenres(request.genreIds()))
                .cast(resolveCast(request.cast()))
                .episodes(toEpisodes(request.episodes()))
                .build();

        movie.setSlug(uniqueSlug(request.title(), null));
        movie.normalize();

        return mapper.toDetail(save(movie));
    }

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public MovieDetailDTO update(String id, MovieRequest request) {
        validateSeriesConsistency(request);

        Movie movie = getOrThrow(id);

        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setReleaseDate(request.releaseDate());
        movie.setDurationInMinutes(request.durationInMinutes());
        movie.setPosterUrl(request.posterUrl());
        movie.setSeries(Boolean.TRUE.equals(request.series()));
        movie.setGenres(resolveGenres(request.genreIds()));
        movie.setCast(resolveCast(request.cast()));
        movie.setEpisodes(toEpisodes(request.episodes()));
        movie.setSlug(uniqueSlug(request.title(), id));
        movie.normalize();

        return mapper.toDetail(save(movie));
    }

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public MovieDetailDTO changeStatus(String id, MovieStatus status) {
        Movie movie = getOrThrow(id);

        // Phát hành một phim bộ chưa có tập nào sẽ tạo ra một trang chi tiết trống trơn cho khách.
        if (status == MovieStatus.PUBLISHED && movie.isSeries() && movie.getEpisodes().isEmpty()) {
            throw new BadRequestException("Không thể phát hành phim bộ chưa có tập nào");
        }

        movie.setStatus(status);
        Movie saved = save(movie);
        log.info("Movie {} status changed to {}", id, status);
        return mapper.toDetail(saved);
    }

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public MovieDetailDTO replaceEpisodes(String id, List<EpisodeRequest> episodes) {
        Movie movie = getOrThrow(id);

        if (!movie.isSeries()) {
            throw new BadRequestException("Chỉ phim bộ mới có danh sách tập");
        }
        validateNoDuplicateEpisodeNumbers(episodes);

        movie.setEpisodes(toEpisodes(episodes));
        movie.normalize();
        return mapper.toDetail(save(movie));
    }

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public void delete(String id) {
        Movie movie = getOrThrow(id);
        movieRepository.delete(movie);
        log.info("Deleted movie {} ({})", id, movie.getTitle());
    }

    // ---------- Hỗ trợ ----------

    private Movie getOrThrow(String id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim: " + id));
    }

    private Movie save(Movie movie) {
        try {
            return movieRepository.save(movie);
        } catch (DuplicateKeyException e) {
            // Unique index trên slug là chốt chặn cuối; uniqueSlug() ở trên có thể thua trong tình
            // huống hai request tạo phim cùng tiêu đề chạy song song.
            throw new BadRequestException("Slug đã tồn tại, hãy đổi tiêu đề: " + movie.getSlug());
        }
    }

    private void validateSeriesConsistency(MovieRequest request) {
        boolean series = Boolean.TRUE.equals(request.series());
        boolean hasEpisodes = request.episodes() != null && !request.episodes().isEmpty();

        // Từ chối tường minh thay vì âm thầm bỏ đi: gửi kèm tập cho phim lẻ gần như luôn là lỗi
        // phía client, im lặng nuốt sẽ khiến họ tưởng đã lưu thành công.
        if (!series && hasEpisodes) {
            throw new BadRequestException("Phim lẻ không được có danh sách tập");
        }

        validateNoDuplicateEpisodeNumbers(request.episodes());
    }

    /** Hai tập cùng số thứ tự khiến thứ tự phát trở nên không xác định. */
    private void validateNoDuplicateEpisodeNumbers(List<EpisodeRequest> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return;
        }
        Set<Integer> numbers = new HashSet<>();
        for (EpisodeRequest e : episodes) {
            if (!numbers.add(e.episodeNumber())) {
                throw new BadRequestException("Số tập bị trùng: " + e.episodeNumber());
            }
        }
    }

    /**
     * Sinh slug từ tiêu đề, thêm hậu tố khi bị trùng.
     * <p>
     * Hai phim khác nhau có thể trùng tên (bản gốc và bản làm lại), nên slug trùng là chuyện bình
     * thường chứ không phải lỗi — chỉ cần phân biệt được.
     */
    private String uniqueSlug(String title, String currentId) {
        String base = SlugUtils.toSlug(title);
        if (base == null) {
            throw new BadRequestException("Tiêu đề phải chứa ít nhất một chữ hoặc số: " + title);
        }

        String candidate = base;
        int suffix = 2;
        while (isSlugTaken(candidate, currentId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, String currentId) {
        return currentId == null
                ? movieRepository.existsBySlug(slug)
                : movieRepository.existsBySlugAndIdNot(slug, currentId);
    }

    /**
     * Đổi danh sách mã thể loại thành bản sao nhúng, lấy tên từ collection genres.
     * <p>
     * Nạp một lần bằng {@code findAllByIdIn} thay vì tra từng cái trong vòng lặp — nếu không thì
     * một phim 10 thể loại sẽ tốn 10 lượt đi về database.
     */
    private List<GenreRef> resolveGenres(List<String> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> distinctIds = genreIds.stream().distinct().toList();
        Map<String, Genre> found = genreRepository.findAllByIdIn(distinctIds).stream()
                .collect(Collectors.toMap(Genre::getId, Function.identity()));

        List<String> missing = distinctIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Không tìm thấy thể loại: " + String.join(", ", missing));
        }

        return distinctIds.stream()
                .map(found::get)
                .map(g -> GenreRef.builder().id(g.getId()).name(g.getName()).build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Tương tự {@link #resolveGenres}: nạp gộp nghệ sĩ rồi mới dựng bản sao nhúng. */
    private List<CastMember> resolveCast(List<CastMemberRequest> cast) {
        if (cast == null || cast.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> artistIds = cast.stream().map(CastMemberRequest::artistId).distinct().toList();
        Map<String, Artist> found = artistRepository.findAllByIdIn(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));

        List<String> missing = artistIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Không tìm thấy nghệ sĩ: " + String.join(", ", missing));
        }

        return cast.stream()
                .map(c -> CastMember.builder()
                        .artistId(c.artistId())
                        .fullName(found.get(c.artistId()).getFullName())
                        .role(c.role())
                        .characterName(c.characterName())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Episode> toEpisodes(List<EpisodeRequest> episodes) {
        if (episodes == null) {
            return new ArrayList<>();
        }
        return episodes.stream()
                .map(e -> Episode.builder()
                        .episodeNumber(e.episodeNumber())
                        .title(e.title())
                        .videoUrl(e.videoUrl())
                        .durationInMinutes(e.durationInMinutes())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}

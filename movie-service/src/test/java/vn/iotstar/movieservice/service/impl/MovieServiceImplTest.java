package vn.iotstar.movieservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.iotstar.movieservice.mapper.MovieMapper;
import vn.iotstar.movieservice.model.dto.*;
import vn.iotstar.movieservice.model.entity.Artist;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.model.entity.Movie;
import vn.iotstar.movieservice.model.entity.embedded.Episode;
import vn.iotstar.movieservice.model.enums.MovieStatus;
import vn.iotstar.movieservice.repository.ArtistRepository;
import vn.iotstar.movieservice.repository.GenreRepository;
import vn.iotstar.movieservice.repository.MovieRepository;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovieServiceImplTest {

    @Mock private MovieRepository movieRepository;
    @Mock private GenreRepository genreRepository;
    @Mock private ArtistRepository artistRepository;
    @Spy private MovieMapper mapper = new MovieMapper();

    @InjectMocks private MovieServiceImpl service;

    @BeforeEach
    void setUp() {
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movieRepository.existsBySlug(anyString())).thenReturn(false);
        when(movieRepository.existsBySlugAndIdNot(anyString(), anyString())).thenReturn(false);
    }

    private MovieRequest request(String title, boolean series, List<EpisodeRequest> episodes) {
        return new MovieRequest(title, "Mô tả", null, null, null, series, null, null, episodes);
    }

    @Nested
    @DisplayName("tạo phim")
    class Create {

        @Test
        @DisplayName("phim mới luôn ở trạng thái DRAFT, không thể tạo thẳng phim đã phát hành")
        void alwaysStartsAsDraft() {
            service.create(request("Nhà Bà Nữ", false, null));

            ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
            verify(movieRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MovieStatus.DRAFT);
        }

        @Test
        @DisplayName("slug được sinh từ tiêu đề")
        void generatesSlugFromTitle() {
            service.create(request("Nhà Bà Nữ", false, null));

            ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
            verify(movieRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("nha-ba-nu");
        }

        @Test
        @DisplayName("slug trùng được thêm hậu tố thay vì báo lỗi: hai phim cùng tên là bình thường")
        void appendsSuffixWhenSlugTaken() {
            when(movieRepository.existsBySlug("nha-ba-nu")).thenReturn(true);
            when(movieRepository.existsBySlug("nha-ba-nu-2")).thenReturn(false);

            service.create(request("Nhà Bà Nữ", false, null));

            ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
            verify(movieRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("nha-ba-nu-2");
        }

        @Test
        @DisplayName("tiêu đề không có chữ hay số nào thì bị từ chối, không lưu slug null")
        void rejectsTitleWithoutUsableCharacters() {
            assertThatThrownBy(() -> service.create(request("!!!", false, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ít nhất một chữ hoặc số");
        }

        @Test
        @DisplayName("gửi kèm tập cho phim lẻ bị từ chối tường minh chứ không âm thầm bỏ qua")
        void rejectsEpisodesOnNonSeries() {
            List<EpisodeRequest> episodes =
                    List.of(new EpisodeRequest(1, "Tập 1", "http://v/1", null));

            assertThatThrownBy(() -> service.create(request("Phim Lẻ", false, episodes)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Phim lẻ không được có danh sách tập");
        }

        @Test
        @DisplayName("số tập trùng nhau bị từ chối vì thứ tự phát sẽ không xác định")
        void rejectsDuplicateEpisodeNumbers() {
            List<EpisodeRequest> episodes = List.of(
                    new EpisodeRequest(1, "Tập 1", "http://v/1", null),
                    new EpisodeRequest(1, "Tập 1 bản khác", "http://v/2", null));

            assertThatThrownBy(() -> service.create(request("Phim Bộ", true, episodes)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tập bị trùng");
        }

        @Test
        @DisplayName("thể loại không tồn tại bị từ chối, không lưu tham chiếu treo")
        void rejectsUnknownGenre() {
            when(genreRepository.findAllByIdIn(any())).thenReturn(List.of());

            MovieRequest req = new MovieRequest("Phim", "Mô tả", null, null, null, false,
                    List.of("khong-ton-tai"), null, null);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy thể loại");
        }

        @Test
        @DisplayName("tên thể loại được sao chép vào phim để đọc chi tiết không phải join")
        void copiesGenreNameIntoMovie() {
            Genre genre = Genre.builder().id("g1").name("Hành động").slug("hanh-dong").build();
            when(genreRepository.findAllByIdIn(List.of("g1"))).thenReturn(List.of(genre));

            MovieRequest req = new MovieRequest("Phim", "Mô tả", null, null, null, false,
                    List.of("g1"), null, null);
            service.create(req);

            ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
            verify(movieRepository).save(captor.capture());
            assertThat(captor.getValue().getGenres())
                    .singleElement()
                    .satisfies(g -> {
                        assertThat(g.getId()).isEqualTo("g1");
                        assertThat(g.getName()).isEqualTo("Hành động");
                    });
        }

        @Test
        @DisplayName("tên nghệ sĩ được sao chép vào ê-kíp của phim")
        void copiesArtistNameIntoCast() {
            Artist artist = Artist.builder().id("a1").fullName("Trấn Thành").build();
            when(artistRepository.findAllByIdIn(List.of("a1"))).thenReturn(List.of(artist));

            MovieRequest req = new MovieRequest("Phim", "Mô tả", null, null, null, false, null,
                    List.of(new CastMemberRequest("a1", "ACTOR", "Ba Sang")), null);
            service.create(req);

            ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
            verify(movieRepository).save(captor.capture());
            assertThat(captor.getValue().getCast())
                    .singleElement()
                    .satisfies(c -> {
                        assertThat(c.getFullName()).isEqualTo("Trấn Thành");
                        assertThat(c.getCharacterName()).isEqualTo("Ba Sang");
                    });
        }
    }

    @Nested
    @DisplayName("đọc công khai")
    class PublicRead {

        @Test
        @DisplayName("phim DRAFT trả 404 chứ không phải 403: 403 đã tiết lộ phim đó tồn tại")
        void draftMovieIsNotFound() {
            Movie draft = Movie.builder().id("m1").title("Chưa phát hành")
                    .status(MovieStatus.DRAFT).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(draft));

            assertThatThrownBy(() -> service.findPublishedByIdOrSlug("m1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("tra được phim đã phát hành bằng slug khi id không khớp")
        void fallsBackToSlugLookup() {
            Movie published = Movie.builder().id("m1").slug("nha-ba-nu").title("Nhà Bà Nữ")
                    .status(MovieStatus.PUBLISHED).build();
            when(movieRepository.findById("nha-ba-nu")).thenReturn(Optional.empty());
            when(movieRepository.findBySlug("nha-ba-nu")).thenReturn(Optional.of(published));

            MovieDetailDTO result = service.findPublishedByIdOrSlug("nha-ba-nu");

            assertThat(result.title()).isEqualTo("Nhà Bà Nữ");
        }
    }

    @Nested
    @DisplayName("đổi trạng thái")
    class ChangeStatus {

        @Test
        @DisplayName("không cho phát hành phim bộ chưa có tập nào")
        void cannotPublishSeriesWithoutEpisodes() {
            Movie series = Movie.builder().id("m1").title("Phim Bộ").isSeries(true)
                    .status(MovieStatus.DRAFT).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(series));

            assertThatThrownBy(() -> service.changeStatus("m1", MovieStatus.PUBLISHED))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("chưa có tập nào");
        }

        @Test
        @DisplayName("phim bộ có tập thì phát hành được")
        void canPublishSeriesWithEpisodes() {
            Movie series = Movie.builder().id("m1").title("Phim Bộ").isSeries(true)
                    .status(MovieStatus.DRAFT)
                    .episodes(new java.util.ArrayList<>(List.of(
                            Episode.builder().episodeNumber(1).title("Tập 1").build())))
                    .build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(series));

            MovieDetailDTO result = service.changeStatus("m1", MovieStatus.PUBLISHED);

            assertThat(result.status()).isEqualTo(MovieStatus.PUBLISHED);
        }

        @Test
        @DisplayName("phim lẻ không cần tập vẫn phát hành được")
        void canPublishStandaloneMovie() {
            Movie movie = Movie.builder().id("m1").title("Phim Lẻ").isSeries(false)
                    .status(MovieStatus.DRAFT).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(movie));

            assertThat(service.changeStatus("m1", MovieStatus.PUBLISHED).status())
                    .isEqualTo(MovieStatus.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("thay danh sách tập")
    class ReplaceEpisodes {

        @Test
        @DisplayName("từ chối khi phim không phải phim bộ")
        void rejectsForNonSeries() {
            Movie movie = Movie.builder().id("m1").title("Phim Lẻ").isSeries(false).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(movie));

            assertThatThrownBy(() -> service.replaceEpisodes("m1",
                    List.of(new EpisodeRequest(1, "Tập 1", "http://v/1", null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chỉ phim bộ");
        }

        @Test
        @DisplayName("số tập trùng bị chặn ở cả đường đi này, không chỉ khi tạo phim")
        void rejectsDuplicateNumbers() {
            Movie series = Movie.builder().id("m1").title("Phim Bộ").isSeries(true).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(series));

            assertThatThrownBy(() -> service.replaceEpisodes("m1", List.of(
                    new EpisodeRequest(2, "Tập 2", "http://v/2", null),
                    new EpisodeRequest(2, "Tập 2 khác", "http://v/3", null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tập bị trùng");
        }

        @Test
        @DisplayName("tập được lưu theo thứ tự tăng dần bất kể thứ tự client gửi")
        void storesEpisodesSorted() {
            Movie series = Movie.builder().id("m1").title("Phim Bộ").isSeries(true).build();
            when(movieRepository.findById("m1")).thenReturn(Optional.of(series));

            MovieDetailDTO result = service.replaceEpisodes("m1", List.of(
                    new EpisodeRequest(3, "Tập 3", "http://v/3", null),
                    new EpisodeRequest(1, "Tập 1", "http://v/1", null),
                    new EpisodeRequest(2, "Tập 2", "http://v/2", null)));

            assertThat(result.episodes()).extracting(EpisodeDTO::episodeNumber)
                    .containsExactly(1, 2, 3);
        }
    }

    @Test
    @DisplayName("không tìm thấy phim thì ném ResourceNotFoundException")
    void throwsWhenMovieMissing() {
        when(movieRepository.findById("khong-co")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdForAdmin("khong-co"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

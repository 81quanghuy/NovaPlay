package vn.iotstar.movieservice.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.iotstar.movieservice.model.entity.embedded.Episode;
import vn.iotstar.movieservice.model.enums.MovieStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test hồi quy cho {@code @Builder.Default}.
 * <p>
 * Không có annotation đó, Lombok âm thầm bỏ qua giá trị khởi tạo của field và builder trả về
 * false/null. user-service từng dính đúng lỗi này ở {@code UserProfile.active}, khiến mọi user
 * đăng ký qua Kafka đều bị vô hiệu hoá — không có test nào bắt được cho tới khi chạy thật.
 */
class MovieDefaultsTest {

    @Test
    @DisplayName("builder không truyền status thì phim phải ở DRAFT, không phải null")
    void statusDefaultsToDraft() {
        Movie movie = Movie.builder().title("Phim Test").build();

        assertThat(movie.getStatus()).isEqualTo(MovieStatus.DRAFT);
    }

    @Test
    @DisplayName("builder không truyền isSeries thì phải là false, không phải null")
    void seriesDefaultsToFalse() {
        Movie movie = Movie.builder().title("Phim Test").build();

        assertThat(movie.isSeries()).isFalse();
    }

    @Test
    @DisplayName("các collection mặc định là list rỗng chứ không phải null")
    void collectionsDefaultToEmptyLists() {
        Movie movie = Movie.builder().title("Phim Test").build();

        assertThat(movie.getGenres()).isNotNull().isEmpty();
        assertThat(movie.getCast()).isNotNull().isEmpty();
        assertThat(movie.getEpisodes()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("normalize sắp xếp tập theo số thứ tự tăng dần")
    void normalizeSortsEpisodes() {
        Movie movie = Movie.builder()
                .title("Phim Bộ")
                .isSeries(true)
                .episodes(new java.util.ArrayList<>(List.of(
                        Episode.builder().episodeNumber(3).title("Tập 3").build(),
                        Episode.builder().episodeNumber(1).title("Tập 1").build(),
                        Episode.builder().episodeNumber(2).title("Tập 2").build())))
                .build();

        movie.normalize();

        assertThat(movie.getEpisodes()).extracting(Episode::getEpisodeNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("normalize xoá tập khỏi phim lẻ để không còn dữ liệu mâu thuẫn")
    void normalizeClearsEpisodesForNonSeries() {
        Movie movie = Movie.builder()
                .title("Phim Lẻ")
                .isSeries(false)
                .episodes(new java.util.ArrayList<>(List.of(
                        Episode.builder().episodeNumber(1).title("Tập lạc").build())))
                .build();

        movie.normalize();

        assertThat(movie.getEpisodes()).isEmpty();
    }

    @Test
    @DisplayName("normalize cắt khoảng trắng thừa và hạ slug về chữ thường")
    void normalizeTrimsAndLowercases() {
        Movie movie = Movie.builder()
                .title("  Phim Có Khoảng Trắng  ")
                .description("  Mô tả  ")
                .slug("  Phim-CO-Khoang-Trang  ")
                .build();

        movie.normalize();

        assertThat(movie.getTitle()).isEqualTo("Phim Có Khoảng Trắng");
        assertThat(movie.getDescription()).isEqualTo("Mô tả");
        assertThat(movie.getSlug()).isEqualTo("phim-co-khoang-trang");
    }
}

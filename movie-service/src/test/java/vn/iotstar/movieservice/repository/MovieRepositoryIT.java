package vn.iotstar.movieservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.DockerClientFactory;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.iotstar.movieservice.config.security.MongoAuditingConfig;
import vn.iotstar.movieservice.model.entity.Movie;
import vn.iotstar.movieservice.model.entity.embedded.CastMember;
import vn.iotstar.movieservice.model.entity.embedded.GenreRef;
import vn.iotstar.movieservice.model.enums.MovieStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.iotstar.movieservice.utils.Constants.*;

/**
 * Integration test cho phần hiện thực tuỳ biến của repository, chạy trên MongoDB thật.
 * <p>
 * Những thứ được kiểm ở đây — toán tử vị trí {@code $}, {@code arrayFilters}, {@code $pull} và
 * truy vấn text index — đều là hành vi của MongoDB chứ không phải của code Java, nên mock không
 * chứng minh được gì. Chính loại lỗi này đã lọt qua unit test của user-service và chỉ lộ ra khi
 * chạy thật.
 */
@Testcontainers
@DataMongoTest
// @DataMongoTest chỉ scan repository/document, không kéo theo @Configuration của ứng dụng —
// thiếu import này thì auditorAware không được đăng ký, @EnableMongoAuditing không kích hoạt,
// và @CreatedDate/@CreatedBy im lặng bị bỏ qua dù @Version đã đúng.
@Import(MongoAuditingConfig.class)
@EnabledIf("dockerAvailable")
class MovieRepositoryIT {

    /**
     * Bỏ qua thay vì làm hỏng build khi máy không chạy được Docker.
     * <p>
     * Không có điều kiện này, {@code mvn verify} sẽ đỏ trên mọi máy dev không bật Docker — người
     * ta sẽ nhanh chóng học cách bỏ qua kết quả build, và đó là thứ đắt hơn nhiều so với việc bỏ
     * lỡ một test. CI có Docker nên ở đó test vẫn chạy thật.
     */
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private MovieRepository movieRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(Movie.class);

        // MongoIndexInitializer là ApplicationRunner nên không chạy trong slice test; text index
        // phải tạo tay, nếu không truy vấn theo từ khoá sẽ lỗi "text index required".
        mongoTemplate.indexOps(Movie.class).createIndex(TextIndexDefinition.builder()
                .named(IDX_MOVIE_TITLE_TEXT)
                .onField(MOVIE_TITLE)
                .build());
    }

    private Movie movie(String id, String title, MovieStatus status, boolean series,
                        List<GenreRef> genres, List<CastMember> cast) {
        Movie m = Movie.builder()
                .id(id)
                .slug(id)
                .title(title)
                .description("Mô tả " + title)
                .releaseDate(LocalDate.of(2024, 1, 1))
                .isSeries(series)
                .status(status)
                .genres(new ArrayList<>(genres))
                .cast(new ArrayList<>(cast))
                .build();
        return movieRepository.save(m);
    }

    @Test
    @DisplayName("auditing điền createdAt dù id được gán sẵn — @Version là thứ khiến việc này chạy")
    void auditingPopulatesCreatedAtDespitePreAssignedId() {
        Movie saved = movie("m1", "Phim Một", MovieStatus.PUBLISHED, false, List.of(), List.of());

        assertThat(saved.getCreatedAt())
                .as("createdAt null nghĩa là Spring Data coi document là đã tồn tại và bỏ qua auditing")
                .isNotNull();
    }

    @Test
    @DisplayName("lọc theo trạng thái chỉ trả phim đã phát hành")
    void filtersByStatus() {
        movie("m1", "Đã phát hành", MovieStatus.PUBLISHED, false, List.of(), List.of());
        movie("m2", "Bản nháp", MovieStatus.DRAFT, false, List.of(), List.of());

        Page<Movie> result = movieRepository.search(
                new MovieSearchCriteria(MovieStatus.PUBLISHED, null, null, null),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Movie::getId).containsExactly("m1");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("lọc theo thể loại khớp vào mảng nhúng trong phim")
    void filtersByEmbeddedGenre() {
        GenreRef action = GenreRef.builder().id("g1").name("Hành động").build();
        GenreRef comedy = GenreRef.builder().id("g2").name("Hài").build();

        movie("m1", "Phim Hành Động", MovieStatus.PUBLISHED, false, List.of(action), List.of());
        movie("m2", "Phim Hài", MovieStatus.PUBLISHED, false, List.of(comedy), List.of());

        Page<Movie> result = movieRepository.search(
                new MovieSearchCriteria(MovieStatus.PUBLISHED, "g1", null, null),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Movie::getId).containsExactly("m1");
    }

    @Test
    @DisplayName("tìm theo từ khoá dùng text index trên tiêu đề")
    void searchesByTitleText() {
        movie("m1", "Nhà Bà Nữ", MovieStatus.PUBLISHED, false, List.of(), List.of());
        movie("m2", "Mai", MovieStatus.PUBLISHED, false, List.of(), List.of());

        Page<Movie> result = movieRepository.search(
                new MovieSearchCriteria(MovieStatus.PUBLISHED, null, null, "Nhà"),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Movie::getId).containsExactly("m1");
    }

    @Test
    @DisplayName("tổng số phần tử là của toàn bộ kết quả, không phải của riêng trang hiện tại")
    void countReflectsFullResultNotJustPage() {
        for (int i = 1; i <= 5; i++) {
            movie("m" + i, "Phim " + i, MovieStatus.PUBLISHED, false, List.of(), List.of());
        }

        Page<Movie> firstPage = movieRepository.search(
                new MovieSearchCriteria(MovieStatus.PUBLISHED, null, null, null),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "title")));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("đổi tên thể loại lan sang mọi phim đang tham chiếu")
    void renamesEmbeddedGenreAcrossMovies() {
        GenreRef action = GenreRef.builder().id("g1").name("Hành động").build();
        movie("m1", "Phim Một", MovieStatus.PUBLISHED, false, List.of(action), List.of());
        movie("m2", "Phim Hai", MovieStatus.PUBLISHED, false, List.of(action), List.of());
        movie("m3", "Phim Ba", MovieStatus.PUBLISHED, false, List.of(), List.of());

        long affected = movieRepository.renameEmbeddedGenre("g1", "Hành Động & Phiêu Lưu");

        assertThat(affected).isEqualTo(2);
        assertThat(movieRepository.findById("m1").orElseThrow().getGenres())
                .singleElement()
                .satisfies(g -> assertThat(g.getName()).isEqualTo("Hành Động & Phiêu Lưu"));
        assertThat(movieRepository.findById("m3").orElseThrow().getGenres()).isEmpty();
    }

    @Test
    @DisplayName("gỡ thể loại khỏi mảng nhúng của mọi phim")
    void removesEmbeddedGenre() {
        GenreRef action = GenreRef.builder().id("g1").name("Hành động").build();
        GenreRef comedy = GenreRef.builder().id("g2").name("Hài").build();
        movie("m1", "Phim Một", MovieStatus.PUBLISHED, false, List.of(action, comedy), List.of());

        long affected = movieRepository.removeEmbeddedGenre("g1");

        assertThat(affected).isEqualTo(1);
        assertThat(movieRepository.findById("m1").orElseThrow().getGenres())
                .singleElement()
                .satisfies(g -> assertThat(g.getId()).isEqualTo("g2"));
    }

    @Test
    @DisplayName("đổi tên nghệ sĩ cập nhật MỌI dòng ê-kíp của người đó, không chỉ dòng đầu tiên")
    void renamesEveryCastEntryForSameArtist() {
        // Một người vừa đạo diễn vừa đóng vai — toán tử $ chỉ sửa phần tử khớp đầu tiên nên
        // trường hợp này là chỗ duy nhất lộ ra việc thiếu arrayFilters.
        CastMember asDirector = CastMember.builder()
                .artistId("a1").fullName("Trấn Thành").role("DIRECTOR").build();
        CastMember asActor = CastMember.builder()
                .artistId("a1").fullName("Trấn Thành").role("ACTOR").characterName("Ba Sang").build();
        CastMember other = CastMember.builder()
                .artistId("a2").fullName("Lê Giang").role("ACTOR").build();

        movie("m1", "Nhà Bà Nữ", MovieStatus.PUBLISHED, false, List.of(),
                List.of(asDirector, asActor, other));

        long affected = movieRepository.renameEmbeddedArtist("a1", "Huỳnh Trấn Thành");

        assertThat(affected).isEqualTo(1);

        List<CastMember> cast = movieRepository.findById("m1").orElseThrow().getCast();
        assertThat(cast).filteredOn(c -> "a1".equals(c.getArtistId()))
                .hasSize(2)
                .allSatisfy(c -> assertThat(c.getFullName()).isEqualTo("Huỳnh Trấn Thành"));
        assertThat(cast).filteredOn(c -> "a2".equals(c.getArtistId()))
                .singleElement()
                .satisfies(c -> assertThat(c.getFullName()).isEqualTo("Lê Giang"));
    }

    @Test
    @DisplayName("existsByGenresId nhận ra thể loại đang được dùng, chặn xoá nhầm")
    void detectsGenreInUse() {
        GenreRef action = GenreRef.builder().id("g1").name("Hành động").build();
        movie("m1", "Phim Một", MovieStatus.PUBLISHED, false, List.of(action), List.of());

        assertThat(movieRepository.existsByGenresId("g1")).isTrue();
        assertThat(movieRepository.existsByGenresId("g-khong-dung")).isFalse();
    }

    @Test
    @DisplayName("existsByCastArtistId nhận ra nghệ sĩ đang xuất hiện trong phim")
    void detectsArtistInUse() {
        CastMember member = CastMember.builder()
                .artistId("a1").fullName("Trấn Thành").role("ACTOR").build();
        movie("m1", "Phim Một", MovieStatus.PUBLISHED, false, List.of(), List.of(member));

        assertThat(movieRepository.existsByCastArtistId("a1")).isTrue();
        assertThat(movieRepository.existsByCastArtistId("a-khong-dung")).isFalse();
    }
}

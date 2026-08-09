package vn.iotstar.movieservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.movieservice.model.entity.embedded.CastMember;
import vn.iotstar.movieservice.model.entity.embedded.Episode;
import vn.iotstar.movieservice.model.entity.embedded.GenreRef;
import vn.iotstar.movieservice.model.enums.MovieStatus;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.movieservice.common.audit.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = Constants.MOVIE_COLLECTION)
@TypeAlias("Movie")
public class Movie extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * Bắt buộc phải có, kể cả khi không dùng optimistic locking.
     * <p>
     * Id được sinh sẵn phía ứng dụng trước khi lưu, nên nếu thiếu {@code @Version}, Spring Data
     * coi document là đã tồn tại và bỏ qua toàn bộ auditing {@code @CreatedDate}/{@code @CreatedBy}.
     * user-service đã dính đúng lỗi này ở {@code FavoriteItem}.
     */
    @Version
    private Long version;

    /** Định danh thân thiện với URL; endpoint chi tiết chấp nhận cả id lẫn slug. */
    @Field(Constants.SLUG)
    private String slug;

    @Field(Constants.MOVIE_TITLE)
    private String title;

    @Field(Constants.MOVIE_DESCRIPTION)
    private String description;

    @Field(Constants.MOVIE_RELEASE_DATE)
    private LocalDate releaseDate;

    @Field(Constants.MOVIE_DURATION)
    private Integer durationInMinutes;

    @Field(Constants.MOVIE_POSTER_URL)
    private String posterUrl;

    // @Builder.Default là bắt buộc trên mọi field có giá trị khởi tạo: không có nó Lombok âm thầm
    // bỏ qua giá trị này và builder sinh ra false/null. Đây đúng là bug mà user-service từng dính
    // với UserProfile.active, khiến mọi user đăng ký qua Kafka đều bị vô hiệu hoá.
    @Builder.Default
    @Field(Constants.MOVIE_IS_SERIES)
    private boolean isSeries = false;

    @Builder.Default
    @Field(Constants.MOVIE_STATUS)
    private MovieStatus status = MovieStatus.DRAFT;

    @Builder.Default
    @Field(Constants.MOVIE_GENRES)
    private List<GenreRef> genres = new ArrayList<>();

    @Builder.Default
    @Field(Constants.MOVIE_CAST)
    private List<CastMember> cast = new ArrayList<>();

    @Builder.Default
    @Field(Constants.MOVIE_EPISODES)
    private List<Episode> episodes = new ArrayList<>();

    /**
     * Chuẩn hoá trước khi lưu.
     * <p>
     * Sắp xếp tập ngay tại đây thay vì lúc đọc: thứ tự tập là thuộc tính của dữ liệu, và mọi
     * người đọc sẽ nhận được đúng thứ tự mà không phải nhớ tự sắp lại.
     */
    public void normalize() {
        if (title != null) title = title.trim();
        if (description != null) description = description.trim();
        if (posterUrl != null) posterUrl = posterUrl.trim();
        if (slug != null) slug = slug.trim().toLowerCase();
        if (status == null) status = MovieStatus.DRAFT;

        if (genres == null) genres = new ArrayList<>();
        if (cast == null) cast = new ArrayList<>();
        if (episodes == null) episodes = new ArrayList<>();

        // Phim lẻ không có tập; giữ lại tập sẽ tạo dữ liệu mâu thuẫn khi admin đổi is_series.
        if (!isSeries) {
            episodes.clear();
        } else {
            episodes.sort(Comparator.comparing(
                    Episode::getEpisodeNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }
}

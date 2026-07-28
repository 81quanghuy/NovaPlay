package vn.iotstar.movieservice.utils;

/**
 * Tên collection và tên field trong MongoDB.
 * <p>
 * Tên field được khai báo tường minh qua {@code @Field} thay vì để Spring Data suy ra từ tên
 * thuộc tính Java: đổi tên thuộc tính khi refactor sẽ không âm thầm làm mất dữ liệu đã lưu.
 */
public final class Constants {

    private Constants() {}

    // --- Collection ---
    public static final String MOVIE_COLLECTION = "movies";
    public static final String GENRE_COLLECTION = "genres";
    public static final String ARTIST_COLLECTION = "artists";

    // --- Field dùng chung ---
    public static final String ID = "_id";
    public static final String SLUG = "slug";

    // --- Field của movie ---
    public static final String MOVIE_TITLE = "title";
    public static final String MOVIE_DESCRIPTION = "description";
    public static final String MOVIE_RELEASE_DATE = "release_date";
    public static final String MOVIE_DURATION = "duration_in_minutes";
    public static final String MOVIE_POSTER_URL = "poster_url";
    public static final String MOVIE_IS_SERIES = "is_series";
    public static final String MOVIE_STATUS = "status";
    public static final String MOVIE_GENRES = "genres";
    public static final String MOVIE_CAST = "cast";
    public static final String MOVIE_EPISODES = "episodes";

    // Đường dẫn lồng nhau, dùng cho index multikey và truy vấn lọc theo thể loại.
    public static final String MOVIE_GENRES_ID = MOVIE_GENRES + ".id";

    // --- Field nhúng trong genres[] của movie ---
    public static final String GENRE_REF_ID = "id";
    public static final String GENRE_REF_NAME = "name";

    // --- Field nhúng trong cast[] của movie ---
    public static final String CAST_ARTIST_ID = "artist_id";
    public static final String CAST_FULL_NAME = "full_name";
    public static final String CAST_ROLE = "role";
    public static final String CAST_CHARACTER_NAME = "character_name";

    // --- Field nhúng trong episodes[] của movie ---
    public static final String EPISODE_NUMBER = "episode_number";
    public static final String EPISODE_TITLE = "title";
    public static final String EPISODE_VIDEO_URL = "video_url";
    public static final String EPISODE_DURATION = "duration_in_minutes";

    // --- Field của genre ---
    public static final String GENRE_NAME = "name";

    // --- Field của artist ---
    public static final String ARTIST_FULL_NAME = "full_name";
    public static final String ARTIST_BIO = "bio";
    public static final String ARTIST_AVATAR_URL = "avatar_url";

    // --- Tên index ---
    public static final String IDX_MOVIE_SLUG = "uk_movie_slug";
    public static final String IDX_MOVIE_STATUS_RELEASE = "idx_movie_status_release";
    public static final String IDX_MOVIE_GENRE = "idx_movie_genre";
    public static final String IDX_MOVIE_TITLE_TEXT = "txt_movie_title";
    public static final String IDX_GENRE_NAME = "uk_genre_name";
    public static final String IDX_GENRE_SLUG = "uk_genre_slug";
    public static final String IDX_ARTIST_FULL_NAME = "idx_artist_full_name";
}

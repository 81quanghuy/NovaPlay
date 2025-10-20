package vn.iotstar.movieservice.utils;

public final class Constants {

    private Constants() {}

    // Table Names
    public static final String MOVIE_TABLE = "movies";
    public static final String GENRE_TABLE = "genres";
    public static final String ARTIST_TABLE = "artists";
    public static final String EPISODE_TABLE = "episodes";
    public static final String MOVIE_GENRE_TABLE = "movie_genres";
    public static final String MOVIE_ARTIST_TABLE = "movie_artists";

    // Common Columns
    public static final String ID = "id";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    // Movie Columns
    public static final String MOVIE_TITLE = "title";
    public static final String MOVIE_DESCRIPTION = "description";
    public static final String MOVIE_RELEASE_DATE = "release_date";
    public static final String MOVIE_DURATION = "duration_in_minutes";
    public static final String MOVIE_POSTER_URL = "poster_url";
    public static final String MOVIE_IS_SERIES = "is_series";

    // Genre Columns
    public static final String GENRE_NAME = "name";

    // Artist Columns
    public static final String ARTIST_FULL_NAME = "full_name";
    public static final String ARTIST_BIO = "bio";

    // Episode Columns
    public static final String EPISODE_MOVIE_ID = "movie_id";
    public static final String EPISODE_NUMBER = "episode_number";
    public static final String EPISODE_TITLE = "title";
    public static final String EPISODE_VIDEO_URL = "video_url";

    // MovieArtist Columns
    public static final String MA_MOVIE_ID = "movie_id";
    public static final String MA_ARTIST_ID = "artist_id";
    public static final String MA_ROLE = "role";
    public static final String MA_CHARACTER_NAME = "character_name";
}
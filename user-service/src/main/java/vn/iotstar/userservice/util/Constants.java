package vn.iotstar.userservice.util;
/**
 * Constants class to hold all the constant values used in the application.
 * This class is not meant to be instantiated.
 */
public class Constants {

    private Constants() {
        throw  new UnsupportedOperationException("Utility class");
    }
    // User Table Constants
    public static final String PARAM_USER_ID = "userId";
    // link avatar default
    public static final String DEFAULT_AVATAR_URL = "https://i.imgur.com/iP4sAgI.png";

    public static final String USER_TABLE_NAME = "users";
    public static final String USER_ID_COLUMN = "user_id";
    public static final String ACCOUNT_ID_COLUMN = "account_id";
    public static final String AVATAR_COLUMN = "avatar";
    public static final String GENDER_COLUMN = "gender";
    public static final String USER_NAME_COLUMN = "user_name";
    public static final String ADDRESS_COLUMN = "address";
    public static final String DOB_COLUMN = "day_of_birth";
    public static final String IS_ONLINE_COLUMN = "is_user_online";

    // Watch History Table Constants
    public static final String WATCH_HISTORY_TABLE_NAME = "watch_history";
    public static final String WATCH_HISTORY_ID_COLUMN = "watch_history_id";
    public static final String WATCH_HISTORY_USER_ID_COLUMN = "user_id";
    public static final String WATCH_HISTORY_MOVIE_ID_COLUMN = "movie_id";
    public static final String WATCH_HISTORY_WATCHED_AT_COLUMN = "watched_at";
    public static final String WATCH_HISTORY_TOTAL_WATCH_TIME_COLUMN = "watched_time";

    // FavoriteMovies Table Constants
    public static final String FAVORITE_MOVIES_TABLE_NAME = "favorite_movies";
    public static final String FAVORITE_MOVIES_ID_COLUMN = "favorite_movies_id";
    public static final String FAVORITE_MOVIES_USER_ID_COLUMN = "user_id";
    public static final String FAVORITE_MOVIES_MOVIE_ID_COLUMN = "movie_id";
    public static final String FAVORITE_MOVIES_ADDED_AT_COLUMN = "added_at";

}

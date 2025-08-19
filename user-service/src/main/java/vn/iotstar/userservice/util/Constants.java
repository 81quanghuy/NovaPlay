package vn.iotstar.userservice.util;
/**
 * Constants class to hold all the constant values used in the application.
 * This class is not meant to be instantiated.
 */
public class Constants {

    private Constants() {
        throw  new UnsupportedOperationException("Utility class");
    }

    // General Constants
    public static final String UUID_CONST = "uuid";

    // User Table Constants
    public static final String PARAM_USER_ID = "userId";
    // link avatar default
    public static final String DEFAULT_AVATAR_URL = "https://i.imgur.com/iP4sAgI.png";

    // User Profile Table Constants
    public static final String USER_PROFILE_TABLE_NAME = "user_profiles";
    public static final String USER_ID_COLUMN = "user_id";
    public static final String EMAIL_COLUMN = "email";
    public static final String AVATAR_COLUMN = "avatarUrl";
    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String USER_NAME_COLUMN = "user_name";
    public static final String LOCATE_COLUMN = "locale";
    public static final String PLAN_COLUMN = "plan";
    public static final String IS_ACTIVE_COLUMN = "active";
    public static final String MARKETING_OPT_IN_COLUMN = "marketing_opt_in";
    public static final String MARKETING_OPT_IN_AT_COLUMN = "marketing_opt_in_at";


    // Watch History Table Constants
    public static final String WATCH_PROGRESS_TABLE_NAME = "watch_progress";
    public static final String WATCH_PROGRESS_ID_COLUMN = "id";
    public static final String WATCH_PROGRESS_USER_ID_COLUMN = "profile_id";
    public static final String WATCH_PROGRESS_MOVIE_ID_COLUMN = "movie_id";
    public static final String WATCH_PROGRESS_SERIES_ID_COLUMN = "series_id"; // nullable
    public static final String WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN = "last_watched_at";
    public static final String WATCH_PROGRESS_SEASON_ID_COLUMN = "season_id";
    public static final String WATCH_PROGRESS_EPISODE_ID_COLUMN = "episode_id";
    public static final String WATCH_PROGRESS_POSITION_MS_COLUMN = "position_ms";
    public static final String WATCH_PROGRESS_DURATION_MS_COLUMN = "duration_ms";
    public static final String WATCH_PROGRESS_PERCENT_COLUMN = "percent"; // 0..100
    public static final String WATCH_PROGRESS_COMPLETED_COLUMN = "completed"; // true/false


    // FavoriteMovies Table Constants
    public static final String FAVORITE_ITEM_TABLE_NAME = "FAVORITE_ITEM";
    public static final String FAVORITE_ITEM_ID_COLUMN = "id";
    public static final String FAVORITE_ITEM_USER_ID_COLUMN = "profile_id";
    public static final String FAVORITE_ITEM_MOVIE_ID_COLUMN = "movie_id";
    public static final String FAVORITE_ITEM_MOVIE_TYPE_COLUMN = "movie_type"; // MOVIE/EPISODE/...

    // UNIQUE CONSTRAINTS, nullable = false, length = 64)
    public static final String UK_FAV_PROFILE_CONTENT = "uk_fav_profile_content";
    public static final String UK_USER_PROFILE_EMAIL = "uk_user_profile_email";
    public static final String UK_WATCH_PROGRESS_PROFILE_CONTENT = "uk_wp_profile_content";

    // INDEXES
    public static final String IDX_FAV_PROFILE = "idx_fav_profile";
    public static final String IDX_PROFILE_ACTIVE = "idx_user_profile_active";
    public static final String IDX_WATCH_PROGRESS_PROFILE_CONTENT = "idx_wp_profile_content";
    public static final String IDX_WATCH_PROGRESS_PROFILE_TIME = "idx_wp_profile_time";
}

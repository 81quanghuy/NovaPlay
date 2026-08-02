package vn.iotstar.authservice.util;

/**
 * Holds all constant values used throughout the application.
 * This class is designed as a utility class and should not be instantiated.
 */
public final class Constants {
    private Constants() {
    }

    public static final String USER_TABLE = "users";
    public static final String USER_ID = "user_id";
    public static final String USER_COLUMN_USERNAME = "username";
    public static final String USER_COLUMN_EMAIL = "email";
    public static final String USER_COLUMN_PASSWORD = "password";
    public static final String USER_COLUMN_IS_ACTIVE = "is_active";
    public static final String USER_COLUMN_IS_EMAIL_VERIFIED = "is_email_verified";
    public static final String USER_COLUMN_LAST_LOGIN_AT = "last_login_at";
    public static final String UK_USER_EMAIL = "UK_user_email";
    public static final String UK_USER_USERNAME = "UK_user_username";
    public static final String IDX_USER_IS_ACTIVE = "IDX_user_is_active";
    public static final String ROLE_TABLE = "roles";
    public static final String ROLE_ID = "role_id";
    public static final String ROLE_NAME = "role_name";
    public static final String ROLE_DESCRIPTION = "role_description";
    public static final String UK_ROLE_ROLE_NAME = "UK_role_role_name";
    public static final String USER_ROLE_TABLE = "user_roles";
    public static final String TOKEN_TABLE = "tokens";
    public static final String TOKEN_ID = "token_id";
    public static final String TOKEN = "token";
    public static final String TOKEN_TYPE = "token_type";
    public static final String EXPIRED_AT = "expired_at";
    public static final String IS_REVOKED = "is_revoked";
    public static final String TOKEN_COLUMN_USER_ID = "user_id";
    public static final String UK_TOKEN_VALUE = "UK_token_value";
    public static final String IDX_TOKEN_USER_ID = "IDX_token_user_id";
    public static final String IDX_TOKEN_IS_REVOKED = "IDX_token_is_revoked";
    public static final String PERMISSIONS_TABLE = "permissions";
    public static final String PERMISSION_ID = "permission_id";
    public static final String PERMISSION_NAME = "permission_name";
    public static final String PERMISSION_DESCRIPTION = "permission_description";
    public static final String UK_PERMISSION_NAME = "UK_permission_name";
    public static final String ROLE_PERMISSIONS_TABLE = "role_permissions";

    public static final String TRACE_ID = "traceId";
}
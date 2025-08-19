package vn.iotstar.authservice.util;

/**
 * Holds all constant values used throughout the application.
 * This class is designed as a utility class and should not be instantiated.
 */
public final class Constants {

    // =================================================================
    // USERS TABLE
    // =================================================================

    /**
     * Table name for user USERs.
     */
    public static final String USER_TABLE = "users";

    /**
     * Primary key for the USER entity.
     */
    public static final String USER_ID = "user_id";

    /**
     * Column name for the username field.
     */
    public static final String USER_COLUMN_USERNAME = "username";

    /**
     * Column name for the email field.
     */
    public static final String USER_COLUMN_EMAIL = "email";

    /**
     * Column name for the password field.
     */
    public static final String USER_COLUMN_PASSWORD = "password";

    /**
     * Column name for the active status flag.
     */
    public static final String USER_COLUMN_IS_ACTIVE = "is_active";

    /**
     * Column name for the email verification status.
     */
    public static final String USER_COLUMN_IS_EMAIL_VERIFIED = "is_email_verified";

    /**
     * Column name for the last login timestamp.
     */
    public static final String USER_COLUMN_LAST_LOGIN_AT = "last_login_at";

    /**
     * Column name for the user's roles.
     */
    public static final String PROVIDERS_COLUMN_USER = "user";

    /**
     * Unique constraint name for the user email.
     */
    public static final String UK_USER_EMAIL = "UK_user_email";

    /**
     * Unique constraint name for the user username.
     */
    public static final String UK_USER_USERNAME = "UK_user_username";

    /**
     * Index name for the user active status.
     */
    public static final String IDX_USER_IS_ACTIVE = "IDX_user_is_active";

    // =================================================================
    // ROLES TABLE
    // =================================================================

    /**
     * Table name for roles.
     */
    public static final String ROLE_TABLE = "roles";

    /**
     * Primary key for the Role entity.
     */
    public static final String ROLE_ID = "role_id";

    /**
     * Column name for the role's name.
     */
    public static final String ROLE_NAME = "role_name";

    /**
     * Column name for the role's description.
     */
    public static final String ROLE_DESCRIPTION = "role_description";


    /**
     * Unique constraint name for the role name.
     */
    public static final String UK_ROLE_ROLE_NAME = "UK_role_role_name";
    // =================================================================
    // USER_ROLES JOIN TABLE
    // =================================================================

    /**
     * Join table for the many-to-many relationship between USERs and Roles.
     */
    public static final String USER_ROLE_TABLE = "user_roles";


    // =================================================================
    // TOKENS TABLE (e.g., for Refresh Tokens)
    // =================================================================

    /**
     * Table name for storing tokens.
     */
    public static final String TOKEN_TABLE = "tokens";

    /**
     * Primary key for the Token entity.
     */
    public static final String TOKEN_ID = "token_id";

    /**
     * Column name for the token value (or its hash).
     */
    public static final String TOKEN = "token";

    /**
     * Column name for the token's type (e.g., 'REFRESH_TOKEN').
     */
    public static final String TOKEN_TYPE = "token_type";

    /**
     * Column name for the token's expiration timestamp.
     */
    public static final String EXPIRED_AT = "expired_at";

    /**
     * Column name for the token's revoked status flag.
     */
    public static final String IS_REVOKED = "is_revoked";

    /**
     * Foreign key column name for the user associated with the token.
     */
    public static final String TOKEN_COLUMN_USER_ID = "user_id";

    /**
     * Unique constraint name for the token value.
     */
    public static final String UK_TOKEN_VALUE = "UK_token_value";

    /**
     * Index name for the token's user ID.
     */
    public static final String IDX_TOKEN_USER_ID = "IDX_token_user_id";

    /**
     * Index name for the token's revoked status.
     */
    public static final String IDX_TOKEN_IS_REVOKED = "IDX_token_is_revoked";
    // =================================================================
    // PROVIDERS TABLE (Third-party Authentication)
    // =================================================================

    /**
     * Table name for third-party authentication providers.
     */
    public static final String PROVIDERS_TABLE = "providers";

    /**
     * Primary key for the Provider entity.
     */
    public static final String PROVIDER_ID = "provider_id";

    /**
     * Column name for the provider's name (e.g., 'GOOGLE', 'FACEBOOK').
     */
    public static final String PROVIDER_NAME = "provider_name";

    /**
     * Column name for the user ID (Foreign key to the USERs table).
     */
    public static final String PROVIDER_COLUMN_USER_ID = "user_id";

    /**
     * Column name for the provider's URL.
     */
    public static final String PROVIDER_URL = "provider_url";

    /**
     * Unique constraint name for the combination of provider name and user ID.
     */
    public static final String UK_PROVIDER_NAME_AND_USER_ID = "UK_provider_name_and_user_id";

    /**
     * Unique constraint name for the combination of user ID and provider name.
     */
    public static final String UK_PROVIDER_USER_AND_NAME = "UK_provider_user_and_name";

    /**
     * Index name for the provider's user ID.
     */
    public static final String IDX_PROVIDER_USER_ID = "IDX_provider_user_id";

    // =================================================================
    // PERMISSIONS TABLE (Optional, if needed)
    // =================================================================
    /**
     * Table name for permissions.
     */
    public static final String PERMISSIONS_TABLE = "permissions";

    /**
     * Primary key for the Permission entity.
     */
    public static final String PERMISSION_ID = "permission_id";

    /**
     * Column name for the permission's name.
     */
    public static final String PERMISSION_NAME = "permission_name";
    /**
     * Column name for the permission's description.
     */
    public static final String PERMISSION_DESCRIPTION = "permission_description";

    /**
     * Unique constraint name for the permission name.
     */
    public static final String UK_PERMISSION_NAME = "UK_permission_name";
    // =================================================================
    // ROLE_PERMISSIONS JOIN TABLE (Newly Added Section)
    // =================================================================

    /**
     * Join table for the many-to-many relationship between Roles and Permissions.
     */
    public static final String ROLE_PERMISSIONS_TABLE = "role_permissions";
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Constants() {
    }
}
package vn.iotstar.notificationservice.util;

/**
 * Tên collection và tên field trong MongoDB.
 * <p>
 * Tên field được khai báo tường minh qua {@code @Field} thay vì để Spring Data suy ra từ tên
 * thuộc tính Java: đổi tên thuộc tính khi refactor sẽ không âm thầm làm mất dữ liệu đã lưu.
 */
public final class Constants {

    private Constants() {}

    public static final String NOTIFICATION_COLLECTION = "notifications";

    public static final String ID = "_id";
    public static final String USER_EMAIL = "user_email";
    public static final String READ_AT = "read_at";
    /** Không có @Field override trong {@code AuditableDocument} dùng chung, nên tên lưu trữ thực
     * tế là camelCase theo đúng tên thuộc tính Java, không phải snake_case như các field khác. */
    public static final String CREATED_AT = "createdAt";
    public static final String EXPIRES_AT = "expires_at";

    public static final String IDX_USER_CREATED = "idx_notification_user_created";
    public static final String IDX_USER_UNREAD = "idx_notification_user_unread";
    public static final String IDX_EXPIRES_TTL = "idx_notification_expires_ttl";
}

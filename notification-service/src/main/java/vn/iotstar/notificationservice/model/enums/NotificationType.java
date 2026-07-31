package vn.iotstar.notificationservice.model.enums;

/**
 * Loại thông báo. Quyết định kênh nào được dùng (xem {@code ChannelRoutingPolicy}), template email
 * nào được render và khoá i18n nào được tra.
 */
public enum NotificationType {

    /** Mã xác thực một lần. */
    OTP("send-otp", "mail.otp.subject"),

    /** Tài khoản vừa kích hoạt xong. */
    ACCOUNT_ACTIVATED("welcome", "mail.welcome.subject"),

    /** Yêu cầu đặt lại mật khẩu. */
    PASSWORD_RESET("forgot-password", "mail.password-reset.subject"),

    /** Thông báo nghiệp vụ chung, nội dung do bên phát sự kiện cung cấp. */
    GENERIC("generic", "mail.generic.subject");

    private final String templateName;
    private final String subjectKey;

    NotificationType(String templateName, String subjectKey) {
        this.templateName = templateName;
        this.subjectKey = subjectKey;
    }

    /** Tên template Thymeleaf trong {@code src/main/resources/templates}. */
    public String templateName() {
        return templateName;
    }

    /** Khoá trong bundle {@code messages*.properties} dùng làm tiêu đề email. */
    public String subjectKey() {
        return subjectKey;
    }

    /** Khoá tiêu đề hiển thị trong danh sách thông báo in-app. */
    public String titleKey() {
        return "notification." + name().toLowerCase().replace('_', '-') + ".title";
    }

    /** Khoá nội dung hiển thị trong danh sách thông báo in-app. */
    public String bodyKey() {
        return "notification." + name().toLowerCase().replace('_', '-') + ".body";
    }

    /**
     * Phân giải tên loại nhận từ sự kiện. Tên lạ rơi về {@link #GENERIC} thay vì ném lỗi: bên phát
     * thêm một loại mới không được phép làm chết consumer của bên nhận.
     */
    public static NotificationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return GENERIC;
        }
    }
}

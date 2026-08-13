package vn.iotstar.streamingservice.util;

/**
 * Bản sao độc lập của user-service's {@code Plan} / movie-service's {@code MinPlan} — tên hằng số
 * PHẢI khớp cả hai để so sánh thứ hạng đúng. Thứ tự khai báo = thứ hạng, dùng {@link #ordinal()}
 * qua {@link #atLeast(Plan)}.
 */
public enum Plan {
    MEMBER,
    PLUS,
    PRO;

    /** {@code true} nếu gói này đủ để xem nội dung yêu cầu {@code required}. */
    public boolean atLeast(Plan required) {
        return this.ordinal() >= required.ordinal();
    }

    public static Plan fromName(String name) {
        if (name == null || name.isBlank()) {
            return MEMBER;
        }
        try {
            return Plan.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}

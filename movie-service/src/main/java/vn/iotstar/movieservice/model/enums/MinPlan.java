package vn.iotstar.movieservice.model.enums;

/**
 * Gói cước tối thiểu cần để xem một phim. Bản sao độc lập của user-service's {@code Plan} enum
 * (không có shared lib) — tên hằng số PHẢI khớp để streaming-service so sánh thứ hạng đúng giữa
 * gói cước của user (từ user-service) và ngưỡng của phim (từ đây).
 */
public enum MinPlan {
    MEMBER,
    PLUS,
    PRO
}

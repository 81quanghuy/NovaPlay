package vn.iotstar.mediaservice.util;

/**
 * Tên topic Kafka dùng chung cho toàn hệ thống.
 * <p>
 * Trước đây mỗi service tự khai báo một lớp {@code util.TopicName} riêng, nên cùng một topic
 * tồn tại dưới bốn bản sao ở auth-service, user-service, media-service và email-service. Chỉ cần
 * một bên đổi chuỗi là producer và consumer lệch nhau mà không có lỗi biên dịch nào cảnh báo.
 * <p>
 * Quy ước đặt tên: {@code <động từ>-<danh từ>.v<phiên bản>}, viết thường, nối bằng dấu gạch ngang.
 * Topic chết được đặt hậu tố {@link #DLT_SUFFIX}.
 */
public final class TopicNames {

    /** Yêu cầu gửi email giao dịch (OTP). Producer: auth-service. */
    public static final String SEND_EMAIL = "send-email.v1";

    /** Tài khoản vừa được kích hoạt. Producer: auth-service. */
    public static final String ACTIVATE_ACCOUNT = "activate-account.v1";

    /**
     * Ảnh đã xử lý xong. Producer: media-service. CHỈ dùng cho {@code image/*} — user-service's
     * consumer áp mọi event trên topic này thành cập nhật avatar không điều kiện, publish video
     * vào đây sẽ ghi đè avatar người dùng. Video dùng {@link #VIDEO_SOURCE_READY} riêng.
     */
    public static final String SEND_STATUS_MEDIA = "send-status-media.v1";

    /** Video thô vừa upload xong, sẵn sàng transcode. Producer: media-service. Consumer: transcoding-worker. */
    public static final String VIDEO_SOURCE_READY = "video-source-ready.v1";

    /** HLS transcode xong. Producer: media-service (khi worker gọi PATCH /complete). */
    public static final String VIDEO_TRANSCODE_COMPLETED = "video-transcode-completed.v1";

    /**
     * Yêu cầu gửi thông báo đa kênh. Chưa có producer nào — đây là điểm mở rộng để các service
     * nghiệp vụ (payment, promotion...) phát thông báo mà không cần biết kênh nào sẽ gửi.
     */
    public static final String NOTIFICATION_REQUESTED = "notification.requested.v1";

    /** Coupon vừa được đổi thành công. Producer: promotion-service. */
    public static final String REDEEM_COUPON = "redeem-coupon.v1";

    /** Một lời mời giới thiệu vừa được ghi nhận (PENDING). Producer: promotion-service. */
    public static final String CREATE_REFERRAL = "create-referral.v1";

    /** Referral đủ điều kiện, phần thưởng đã phát hành. Producer: promotion-service. */
    public static final String QUALIFY_REFERRAL = "qualify-referral.v1";

    /** Hậu tố topic chết, khớp với quy ước mặc định của {@code DeadLetterPublishingRecoverer}. */
    public static final String DLT_SUFFIX = ".DLT";

    private TopicNames() {
    }

    /** Trả về tên topic chết tương ứng với {@code topic}. */
    public static String dltOf(String topic) {
        return topic + DLT_SUFFIX;
    }
}

package vn.iotstar.streamingservice.utils;

public final class CacheNames {
    private CacheNames() {}

    /** Kết quả phân giải phim -> mediaId -> VideoManifest — thay đổi hiếm (chỉ khi admin sửa phim/transcode lại). */
    public static final String MANIFEST_RESOLUTION = "manifestResolution";

    /** Gói cước của user — TTL ngắn (5 phút) vì admin có thể đổi gói bất kỳ lúc nào. */
    public static final String USER_PLAN = "userPlan";

    /**
     * Metadata phim theo idOrSlug — TTL rất ngắn (60s). resolvePlayback/upsertProgress/recordView
     * đều gọi movie-service qua đây; phim published ít đổi giữa chừng nên 60s đủ hấp thụ traffic
     * lặp lại trong một phiên xem, mà vẫn không giữ dữ liệu cũ lâu nếu admin sửa/gỡ phim.
     */
    public static final String MOVIE_RESOLUTION = "movieResolution";

    /**
     * Đường phát segment đang active (direct/cloudfront) — TTL 30s, đủ nhanh cho thao tác vận hành
     * mà không thêm Feign call chưa cache vào hot path.
     */
    public static final String DELIVERY_MODE = "deliveryMode";
}

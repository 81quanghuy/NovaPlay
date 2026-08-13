package vn.iotstar.streamingservice.utils;

public final class CacheNames {
    private CacheNames() {}

    /** Kết quả phân giải phim -> mediaId -> VideoManifest — thay đổi hiếm (chỉ khi admin sửa phim/transcode lại). */
    public static final String MANIFEST_RESOLUTION = "manifestResolution";

    /** Gói cước của user — TTL ngắn (5 phút) vì admin có thể đổi gói bất kỳ lúc nào. */
    public static final String USER_PLAN = "userPlan";
}

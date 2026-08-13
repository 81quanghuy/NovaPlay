package vn.iotstar.transcodingworker.util;

public final class TopicNames {

    /** Video thô vừa upload xong. Producer: media-service. Consumer: transcoding-worker (đây). */
    public static final String VIDEO_SOURCE_READY = "video-source-ready.v1";

    private TopicNames() {
    }
}

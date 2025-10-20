package vn.iotstar.mediaservice.util;

public final class Constants {
    private Constants() {}

    // Collection
    public static final String MEDIA_COLLECTION = "media";

    // Field names
    public static final String MEDIA_ID                = "_id";
    public static final String MEDIA_OWNER_ID          = "ownerId";
    public static final String MEDIA_ORIGINAL_FILENAME = "originalFileName";
    public static final String MEDIA_S3_KEY            = "s3Key";
    public static final String MEDIA_CDN_URL           = "cdnUrl";
    public static final String MEDIA_STATUS            = "status";
    public static final String CONTENT_TYPE            = "contentType";
    public static final String SIZE                    = "size";

    // Index names
    public static final String IDX_MEDIA_OWNER_ID          = "idx_media_owner_id";
}

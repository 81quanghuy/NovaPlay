package vn.iotstar.mediaservice.util;

public final class Constants {
    private Constants() {}

    // Collection
    public static final String MEDIA_COLLECTION = "media";
    public static final String VIDEO_MANIFEST_COLLECTION = "video_manifests";

    // Field names
    public static final String MEDIA_ID                = "_id";
    public static final String MEDIA_OWNER_ID          = "ownerId";
    public static final String MEDIA_OWNER_EMAIL       = "ownerEmail";
    public static final String MEDIA_ORIGINAL_FILENAME = "originalFileName";
    public static final String MEDIA_S3_KEY            = "s3Key";
    public static final String MEDIA_CDN_URL           = "cdnUrl";
    public static final String MEDIA_STORAGE_PROVIDER  = "storageProvider";
    public static final String MEDIA_STATUS            = "status";
    public static final String CONTENT_TYPE            = "contentType";
    public static final String SIZE                    = "size";

    // Field names — video_manifests
    public static final String VM_SOURCE_MEDIA_ID       = "sourceMediaId";
    public static final String VM_OWNER_ID              = "ownerId";
    public static final String VM_OWNER_EMAIL           = "ownerEmail";
    public static final String VM_STATUS                = "status";
    public static final String VM_STORAGE_PROVIDER      = "storageProvider";
    public static final String VM_MASTER_MANIFEST_KEY   = "masterManifestS3Key";
    public static final String VM_DURATION_SECONDS      = "durationSeconds";
    public static final String VM_FAILURE_REASON        = "failureReason";
    public static final String VM_ATTEMPT_COUNT         = "attemptCount";
    public static final String VM_PROCESSING_STARTED_AT = "processingStartedAt";
    public static final String VM_RENDITIONS            = "renditions";
    public static final String VM_ENCRYPTION_KEY_CIPHERTEXT = "encryptionKeyCiphertext";

    // Index names
    public static final String IDX_MEDIA_OWNER_ID          = "idx_media_owner_id";
    public static final String IDX_MEDIA_S3_KEY            = "uk_media_s3_key";
    public static final String IDX_VIDEO_MANIFEST_SOURCE   = "uk_video_manifest_source_media_id";
}

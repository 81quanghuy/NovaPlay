package vn.iotstar.mediaservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.util.Constants;
import vn.iotstar.mediaservice.util.VideoManifestStatus;
import vn.iotstar.mediaservice.common.audit.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả transcode HLS của một {@link Media} nguồn (video thô). Tách khỏi {@link Media} thay vì
 * nhồi field vào đó: {@code Media} mô hình đúng MỘT object đã upload, còn ở đây một nguồn có thể
 * sinh ra nhiều rendition (1080p/720p/480p/360p) + một master playlist. Không tạo 1 document/segment
 * — segment chỉ được tham chiếu từ bên trong nội dung file playlist, không bao giờ tra cứu riêng lẻ.
 */
@Document(collection = Constants.VIDEO_MANIFEST_COLLECTION)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VideoManifest extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /** Trỏ về {@link Media#getId()} của video thô. Unique: một nguồn chỉ có một manifest. */
    @Field(Constants.VM_SOURCE_MEDIA_ID)
    @Indexed(unique = true)
    private String sourceMediaId;

    @Field(Constants.VM_OWNER_ID)
    private String ownerId;

    @Field(Constants.VM_OWNER_EMAIL)
    private String ownerEmail;

    @Builder.Default
    @Field(Constants.VM_STATUS)
    private VideoManifestStatus status = VideoManifestStatus.QUEUED;

    /** Sao chép từ {@code Media.getEffectiveStorageProvider()} của nguồn lúc tạo manifest. */
    @Field(Constants.VM_STORAGE_PROVIDER)
    private StorageProvider storageProvider;

    @Field(Constants.VM_MASTER_MANIFEST_KEY)
    private String masterManifestS3Key;

    @Field(Constants.VM_DURATION_SECONDS)
    private Integer durationSeconds;

    @Field(Constants.VM_FAILURE_REASON)
    private String failureReason;

    @Builder.Default
    @Field(Constants.VM_ATTEMPT_COUNT)
    private int attemptCount = 0;

    @Field(Constants.VM_PROCESSING_STARTED_AT)
    private Instant processingStartedAt;

    /**
     * Khoá AES-128 dùng để mã hoá segment HLS, đã được transcoding-worker bọc lại (AES-GCM,
     * base64) bằng bí mật {@code TRANSCODE_KEY_WRAP_SECRET} chia sẻ với streaming-service —
     * KHÔNG BAO GIỜ ở dạng thô. streaming-service tự mở khoá theo từng request phát video, không
     * bao giờ cache/log khoá đã mở.
     */
    @Field(Constants.VM_ENCRYPTION_KEY_CIPHERTEXT)
    private String encryptionKeyCiphertext;

    @Builder.Default
    @Field(Constants.VM_RENDITIONS)
    private List<Rendition> renditions = new ArrayList<>();

    /** Cố ý KHÔNG có cdnUrl vĩnh viễn ở đây — streaming-service ký URL phát theo từng request. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Rendition implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String resolutionLabel;
        private Integer width;
        private Integer height;
        private Integer bitrateKbps;
        private String playlistS3Key;
    }
}

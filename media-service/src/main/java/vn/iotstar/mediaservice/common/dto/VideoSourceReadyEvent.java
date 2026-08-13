package vn.iotstar.mediaservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Publish khi một video thô (không phải ảnh) upload xong. Cố ý tách khỏi {@link MediaReadyEvent} —
 * user-service áp MỌI event trên {@code send-status-media.v1} thành cập nhật avatar không điều
 * kiện, nên video không bao giờ được đi qua topic đó.
 */
@Schema(
        title = "VideoSourceReadyEvent",
        description = "Event báo hiệu video thô đã upload xong, sẵn sàng để transcoding-worker xử lý.",
        requiredMode = Schema.RequiredMode.REQUIRED
)
public record VideoSourceReadyEvent(
        @Schema(description = "Id của VideoManifest vừa tạo (QUEUED)")
        String manifestId,
        @Schema(description = "Id của Media nguồn (video thô)")
        String sourceMediaId,
        String ownerId,
        String ownerEmail,
        @Schema(description = "S3 key của video thô")
        String s3Key,
        @Schema(description = "Tên logic của StorageProvider đã lưu video thô")
        String storageProvider,
        String contentType,
        Long sizeBytes
) {
}

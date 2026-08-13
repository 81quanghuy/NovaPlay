package vn.iotstar.mediaservice.common.dto;

/**
 * Publish khi một manifest chuyển sang READY. Không có consumer bắt buộc nào ngay bây giờ — đây
 * là điểm mở rộng (vd notification-service báo cho admin). Cố tình KHÔNG mang cdnUrl: URL phát
 * phải được ký theo từng request bởi streaming-service, không bake vào một event vĩnh viễn.
 */
public record VideoTranscodeCompletedEvent(
        String manifestId,
        String sourceMediaId,
        String ownerId,
        String ownerEmail
) {
}

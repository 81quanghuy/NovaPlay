package vn.iotstar.streamingservice.service.client.dto;

import java.util.List;

/** Chỉ mang field streaming-service thực sự cần từ media-service's {@code VideoManifest}. */
public record VideoManifestResponse(
        String id,
        String sourceMediaId,
        String status,
        String storageProvider,
        String masterManifestS3Key,
        Integer durationSeconds,
        String encryptionKeyCiphertext,
        List<RenditionResponse> renditions
) {
}

package vn.iotstar.streamingservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.iotstar.streamingservice.common.dto.ApiEnvelope;
import vn.iotstar.streamingservice.config.security.ServiceIdentityFeignConfig;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;

/**
 * Đọc manifest HLS — KHÔNG owner-gate ở phía media-service, nên phải mang danh tính
 * {@code ROLE_SERVICE} (không phải danh tính người xem thật) để vượt qua kiểm tra quyền.
 */
@FeignClient(name = "media-service", url = "${services.media}", contextId = "MediaClientServiceStreaming",
        path = "/api/v1/media/video-manifests", configuration = ServiceIdentityFeignConfig.class)
public interface MediaServiceClient {

    /**
     * Dùng cho cả hai luồng tra cứu: theo {@code mediaId} của phim (lúc resolvePlayback) lẫn theo
     * {@code manifestId} lấy từ playback token (lúc phục vụ HLS) — theo thiết kế của media-service,
     * {@code VideoManifest.id == sourceMediaId} luôn luôn (xem VideoManifestServiceImpl.createQueuedFor),
     * nên một endpoint duy nhất phục vụ cả hai.
     */
    @GetMapping("/by-source/{sourceMediaId}")
    ApiEnvelope<VideoManifestResponse> getBySourceMediaId(@PathVariable String sourceMediaId);
}

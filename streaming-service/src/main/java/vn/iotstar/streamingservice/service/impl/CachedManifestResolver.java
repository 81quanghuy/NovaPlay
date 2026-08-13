package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.exception.ResourceNotFoundException;
import vn.iotstar.streamingservice.service.client.MediaServiceClient;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;
import vn.iotstar.streamingservice.utils.CacheNames;

/**
 * Tách riêng thành bean của nó (không phải method private trong {@code StreamingServiceImpl}):
 * {@code @Cacheable} dựa trên AOP proxy, tự gọi method public cùng lớp qua {@code this.xxx()} sẽ
 * bỏ qua proxy và không cache được gì cả — đây là cạm bẫy kinh điển của Spring AOP.
 */
@Component
@RequiredArgsConstructor
public class CachedManifestResolver {

    private final MediaServiceClient mediaServiceClient;

    // unless: chỉ cache khi READY — manifest QUEUED/PROCESSING/FAILED sẽ đổi trạng thái ngay sau
    // đó, cache lại sẽ khiến người xem thấy mãi mãi "chưa sẵn sàng" dù transcode đã xong.
    @Cacheable(value = CacheNames.MANIFEST_RESOLUTION, key = "#mediaId", unless = "#result.status() != 'READY'")
    public VideoManifestResponse resolveByMediaId(String mediaId) {
        VideoManifestResponse manifest = mediaServiceClient.getBySourceMediaId(mediaId).result();
        if (manifest == null) {
            throw new ResourceNotFoundException("No video manifest for media: " + mediaId);
        }
        return manifest;
    }
}

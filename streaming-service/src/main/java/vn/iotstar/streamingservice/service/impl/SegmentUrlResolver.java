package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.iotstar.streamingservice.service.HlsStorageService;
import vn.iotstar.streamingservice.storage.StorageProvider;
import vn.iotstar.streamingservice.storage.StorageProviderProperties;
import vn.iotstar.streamingservice.util.DeliveryMode;

import java.time.Duration;

/**
 * Điểm chèn DUY NHẤT quyết định segment HLS được phát qua đường nào (direct presigned / CDN) —
 * mọi segment URL của repo chảy qua đây. Tách khỏi {@link HlsStorageService}: service đó chỉ lo
 * storage (đọc object, ký presigned URL), còn "chọn đường nào" là mối quan tâm khác, phụ thuộc
 * cờ vận hành.
 * <p>
 * Quy tắc kết hợp: {@code cloudfront} chỉ có hiệu lực khi provider đang dùng THỰC SỰ có
 * {@code cdnBaseUrl} cấu hình. Cờ bật {@code cloudfront} mà storage hiện tại không có CDN phía sau
 * (vd R2 chưa gắn custom domain) sẽ tự động rơi về {@code direct} — không sinh URL rác trỏ vào một
 * base rỗng.
 */
@Component
@RequiredArgsConstructor
public class SegmentUrlResolver {

    private final HlsStorageService hlsStorageService;
    private final StorageProviderProperties storageProviderProperties;
    private final CachedDeliveryModeResolver deliveryModeResolver;

    public String resolve(StorageProvider provider, String segmentKey, Duration ttl) {
        DeliveryMode mode = deliveryModeResolver.resolve();
        String cdnBaseUrl = storageProviderProperties.get(provider).getCdnBaseUrl();

        if (mode == DeliveryMode.CLOUDFRONT && StringUtils.hasText(cdnBaseUrl)) {
            return trimTrailingSlash(cdnBaseUrl) + "/" + segmentKey;
        }
        return hlsStorageService.presignGetObject(provider, segmentKey, ttl);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

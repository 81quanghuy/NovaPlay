package vn.iotstar.streamingservice.service.impl;

import dev.openfeature.sdk.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.util.DeliveryMode;
import vn.iotstar.streamingservice.utils.CacheNames;

/**
 * Điểm DUY NHẤT hỏi OpenFeature xem segment đang được phát qua đường nào. Tách bean riêng vì
 * {@code @Cacheable} cần đi qua AOP proxy — tự gọi method public trong cùng lớp sẽ bỏ qua cache
 * hoàn toàn (cùng lý do với {@link CachedManifestResolver}).
 * <p>
 * Cache TTL 30s: đủ nhanh để thao tác vận hành (đổi cờ khi CDN có sự cố) có hiệu lực gần như ngay,
 * mà không thêm một lời gọi Feign chưa cache vào hot path phát video.
 */
@Component
@RequiredArgsConstructor
public class CachedDeliveryModeResolver {

    private final Client openFeatureClient;

    @Value("${feature-flags.default-delivery-mode:direct}")
    private String defaultVariant;

    /**
     * Khoá cache là hằng số vì cờ này toàn cục (không theo user/phim). Dùng literal thay vì SpEL
     * trên tham số vì method không có tham số nào.
     */
    @Cacheable(value = CacheNames.DELIVERY_MODE, key = "'global'")
    public DeliveryMode resolve() {
        return DeliveryMode.fromFlagVariant(
                openFeatureClient.getStringValue(DeliveryMode.FLAG_KEY, defaultVariant));
    }
}

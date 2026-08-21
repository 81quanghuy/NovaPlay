package vn.iotstar.streamingservice.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.streamingservice.service.HlsStorageService;
import vn.iotstar.streamingservice.storage.StorageProvider;
import vn.iotstar.streamingservice.storage.StorageProviderProperties;
import vn.iotstar.streamingservice.util.DeliveryMode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentUrlResolverTest {

    @Mock
    private HlsStorageService hlsStorageService;

    @Mock
    private CachedDeliveryModeResolver deliveryModeResolver;

    private final StorageProviderProperties properties = new StorageProviderProperties();

    private SegmentUrlResolver resolver() {
        return new SegmentUrlResolver(hlsStorageService, properties, deliveryModeResolver);
    }

    @Test
    @DisplayName("mode direct -> luôn presigned URL, không đụng cdnBaseUrl")
    void directModeAlwaysPresigns() {
        when(deliveryModeResolver.resolve()).thenReturn(DeliveryMode.DIRECT);
        when(hlsStorageService.presignGetObject(eq(StorageProvider.AWS_S3), eq("media/o1/m1/hls/720p/segment_000.ts"), any(Duration.class)))
                .thenReturn("https://signed.local/media/o1/m1/hls/720p/segment_000.ts?X-Amz-Signature=abc");

        String url = resolver().resolve(StorageProvider.AWS_S3, "media/o1/m1/hls/720p/segment_000.ts", Duration.ofHours(1));

        assertThat(url).isEqualTo("https://signed.local/media/o1/m1/hls/720p/segment_000.ts?X-Amz-Signature=abc");
    }

    @Test
    @DisplayName("mode cloudfront + cdnBaseUrl có cấu hình -> URL sạch, không query string")
    void cloudfrontModeWithCdnConfiguredReturnsCleanUrl() {
        properties.getAwsS3().setCdnBaseUrl("https://cdn-video.novaplay.vn");
        when(deliveryModeResolver.resolve()).thenReturn(DeliveryMode.CLOUDFRONT);

        String url = resolver().resolve(StorageProvider.AWS_S3, "media/o1/m1/hls/720p/segment_000.ts", Duration.ofHours(1));

        assertThat(url).isEqualTo("https://cdn-video.novaplay.vn/media/o1/m1/hls/720p/segment_000.ts");
        assertThat(url).doesNotContain("?");
    }

    @Test
    @DisplayName("mode cloudfront nhưng trailing slash trong cdnBaseUrl -> không double slash")
    void cloudfrontModeTrimsTrailingSlash() {
        properties.getAwsS3().setCdnBaseUrl("https://cdn-video.novaplay.vn/");
        when(deliveryModeResolver.resolve()).thenReturn(DeliveryMode.CLOUDFRONT);

        String url = resolver().resolve(StorageProvider.AWS_S3, "media/o1/m1/hls/720p/segment_000.ts", Duration.ofHours(1));

        assertThat(url).isEqualTo("https://cdn-video.novaplay.vn/media/o1/m1/hls/720p/segment_000.ts");
    }

    @Test
    @DisplayName("mode cloudfront nhưng provider hiện tại KHÔNG có cdnBaseUrl -> tự rơi về presigned, không sinh URL rác")
    void cloudfrontModeWithoutCdnConfiguredFallsBackToDirect() {
        // properties.getCloudflareR2().getCdnBaseUrl() mặc định null/rỗng — chưa cấu hình CDN cho R2.
        when(deliveryModeResolver.resolve()).thenReturn(DeliveryMode.CLOUDFRONT);
        when(hlsStorageService.presignGetObject(eq(StorageProvider.CLOUDFLARE_R2), eq("media/o1/m1/hls/720p/segment_000.ts"), any(Duration.class)))
                .thenReturn("https://r2.local/media/o1/m1/hls/720p/segment_000.ts?sig=xyz");

        String url = resolver().resolve(StorageProvider.CLOUDFLARE_R2, "media/o1/m1/hls/720p/segment_000.ts", Duration.ofHours(1));

        assertThat(url).isEqualTo("https://r2.local/media/o1/m1/hls/720p/segment_000.ts?sig=xyz");
    }
}

package vn.iotstar.streamingservice.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.streamingservice.service.HlsStorageService;
import vn.iotstar.streamingservice.service.client.dto.RenditionResponse;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;
import vn.iotstar.streamingservice.storage.StorageProvider;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HlsManifestServiceImplTest {

    @Mock
    private HlsStorageService hlsStorageService;

    @Mock
    private SegmentUrlResolver segmentUrlResolver;

    private final HlsManifestServiceImpl service = new HlsManifestServiceImpl(null, null);

    private HlsManifestServiceImpl serviceWithMock() {
        return new HlsManifestServiceImpl(hlsStorageService, segmentUrlResolver);
    }

    private VideoManifestResponse manifest(List<RenditionResponse> renditions) {
        return new VideoManifestResponse("m1", "media-1", "READY", "AWS_S3",
                "media/o1/media-1/hls/master.m3u8", 120, "wrapped-key", renditions);
    }

    @Test
    @DisplayName("master playlist liệt kê đủ mọi rendition kèm BANDWIDTH/RESOLUTION đúng và trỏ về endpoint của chính service")
    void masterPlaylistListsAllRenditions() {
        VideoManifestResponse manifest = manifest(List.of(
                new RenditionResponse("720p", 1280, 720, 2800, "media/o1/media-1/hls/720p/playlist.m3u8"),
                new RenditionResponse("360p", 640, 360, 800, "media/o1/media-1/hls/360p/playlist.m3u8")));

        String playlist = service.buildMasterPlaylist(manifest, "tok123");

        assertThat(playlist).startsWith("#EXTM3U");
        assertThat(playlist).contains("BANDWIDTH=2800000,RESOLUTION=1280x720");
        assertThat(playlist).contains("/api/v1/streaming/hls/m1/720p.m3u8?pt=tok123");
        assertThat(playlist).contains("BANDWIDTH=800000,RESOLUTION=640x360");
        assertThat(playlist).contains("/api/v1/streaming/hls/m1/360p.m3u8?pt=tok123");
    }

    @Test
    @DisplayName("rendition playlist: URI khoá trỏ về endpoint phát khoá, segment thành presigned GET URL")
    void renditionPlaylistRewritesKeyAndSegments() {
        HlsManifestServiceImpl svc = serviceWithMock();
        RenditionResponse rendition = new RenditionResponse("720p", 1280, 720, 2800, "media/o1/media-1/hls/720p/playlist.m3u8");
        VideoManifestResponse manifest = manifest(List.of(rendition));

        String raw = "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"encrypted.key\"\n"
                + "#EXTINF:6.000,\n"
                + "segment_000.ts\n"
                + "#EXTINF:6.000,\n"
                + "segment_001.ts\n"
                + "#EXT-X-ENDLIST\n";
        when(hlsStorageService.readTextObject(eq(StorageProvider.AWS_S3), eq(rendition.playlistS3Key())))
                .thenReturn(raw);
        when(segmentUrlResolver.resolve(eq(StorageProvider.AWS_S3), any(), any(Duration.class)))
                .thenAnswer(inv -> "https://signed.local/" + inv.getArgument(1));

        String rewritten = svc.buildRenditionPlaylist(manifest, "720p", "tok123", Duration.ofHours(1));

        assertThat(rewritten).contains("URI=\"/api/v1/streaming/hls/m1/key?pt=tok123\"");
        assertThat(rewritten).contains("https://signed.local/media/o1/media-1/hls/720p/segment_000.ts");
        assertThat(rewritten).contains("https://signed.local/media/o1/media-1/hls/720p/segment_001.ts");
        assertThat(rewritten).contains("#EXT-X-ENDLIST");
    }

    @Test
    @DisplayName("rendition không tồn tại trong manifest thì báo lỗi rõ ràng, không NPE")
    void unknownRenditionThrows() {
        VideoManifestResponse manifest = manifest(List.of(
                new RenditionResponse("720p", 1280, 720, 2800, "media/o1/media-1/hls/720p/playlist.m3u8")));

        org.junit.jupiter.api.Assertions.assertThrows(
                vn.iotstar.streamingservice.exception.ResourceNotFoundException.class,
                () -> service.buildRenditionPlaylist(manifest, "1080p", "tok", Duration.ofHours(1)));
    }
}

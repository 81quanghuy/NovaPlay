package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import vn.iotstar.streamingservice.exception.ResourceNotFoundException;
import vn.iotstar.streamingservice.service.HlsManifestService;
import vn.iotstar.streamingservice.service.HlsStorageService;
import vn.iotstar.streamingservice.service.client.dto.RenditionResponse;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;
import vn.iotstar.streamingservice.storage.StorageProvider;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HlsManifestServiceImpl implements HlsManifestService {

    private static final Pattern KEY_URI_PATTERN = Pattern.compile("URI=\"[^\"]*\"");

    private final HlsStorageService hlsStorageService;
    private final SegmentUrlResolver segmentUrlResolver;

    @Override
    public String buildMasterPlaylist(VideoManifestResponse manifest, String playbackToken) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (RenditionResponse r : manifest.renditions()) {
            long bandwidthBps = r.bitrateKbps() * 1000L;
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidthBps)
                    .append(",RESOLUTION=").append(r.width()).append('x').append(r.height())
                    .append('\n');
            sb.append(renditionUrl(manifest.id(), r.resolutionLabel(), playbackToken)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String buildRenditionPlaylist(VideoManifestResponse manifest, String label, String playbackToken, Duration segmentUrlTtl) {
        RenditionResponse rendition = manifest.renditions().stream()
                .filter(r -> r.resolutionLabel().equals(label))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown rendition '" + label + "' for manifest " + manifest.id()));

        StorageProvider provider = StorageProvider.fromName(manifest.storageProvider());
        String raw = hlsStorageService.readTextObject(provider, rendition.playlistS3Key());
        String dir = dirOf(rendition.playlistS3Key());
        String keyEndpoint = keyUrl(manifest.id(), playbackToken);

        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.stripTrailing();
            if (trimmed.startsWith("#EXT-X-KEY")) {
                Matcher matcher = KEY_URI_PATTERN.matcher(trimmed);
                String rewritten = matcher.find() ? matcher.replaceFirst("URI=\"" + Matcher.quoteReplacement(keyEndpoint) + "\"") : trimmed;
                out.append(rewritten).append('\n');
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                String segmentKey = dir + trimmed;
                out.append(segmentUrlResolver.resolve(provider, segmentKey, segmentUrlTtl)).append('\n');
            } else if (!trimmed.isEmpty() || !line.isEmpty()) {
                out.append(trimmed).append('\n');
            }
        }
        return out.toString();
    }

    private String dirOf(String key) {
        int idx = key.lastIndexOf('/');
        return idx < 0 ? "" : key.substring(0, idx + 1);
    }

    private String renditionUrl(String manifestId, String label, String playbackToken) {
        return UriComponentsBuilder.fromPath("/api/v1/streaming/hls/{manifestId}/{label}.m3u8")
                .queryParam("pt", playbackToken)
                .buildAndExpand(manifestId, label)
                .toUriString();
    }

    private String keyUrl(String manifestId, String playbackToken) {
        return UriComponentsBuilder.fromPath("/api/v1/streaming/hls/{manifestId}/key")
                .queryParam("pt", playbackToken)
                .buildAndExpand(manifestId)
                .toUriString();
    }
}

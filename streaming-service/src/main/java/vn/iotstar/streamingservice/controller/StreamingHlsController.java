package vn.iotstar.streamingservice.controller;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.streamingservice.service.HlsManifestService;
import vn.iotstar.streamingservice.service.KeyUnwrapService;
import vn.iotstar.streamingservice.service.PlaybackTokenService;
import vn.iotstar.streamingservice.service.StreamingService;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;

import java.time.Duration;

/**
 * {@code permitAll} ở tầng Spring Security (xem SecurityConfig) — trình phát HLS (đặc biệt engine
 * gốc của Safari/iOS) không tự gắn được header {@code Authorization} vào từng request tải segment/
 * playlist, nên bảo vệ nội dung nằm hoàn toàn ở playback token {@code pt} (query param), validate
 * thủ công trong từng method — KHÔNG phải một cơ chế Spring Security.
 */
@RestController
@RequestMapping("/api/v1/streaming/hls")
@Tag(name = "Streaming HLS Delivery", description = "Phục vụ playlist/khoá HLS — bảo vệ bằng playback token, không phải session đăng nhập")
@RequiredArgsConstructor
public class StreamingHlsController {

    private static final MediaType HLS_MEDIA_TYPE = MediaType.valueOf("application/vnd.apple.mpegurl");
    private static final Duration MAX_SEGMENT_URL_TTL = Duration.ofHours(6);

    private final StreamingService streamingService;
    private final HlsManifestService hlsManifestService;
    private final PlaybackTokenService playbackTokenService;
    private final KeyUnwrapService keyUnwrapService;

    @Operation(summary = "Master playlist — sinh trực tiếp từ metadata manifest")
    @GetMapping(value = "/{manifestId}/master.m3u8")
    public ResponseEntity<String> masterPlaylist(@PathVariable String manifestId, @RequestParam("pt") String token) {
        playbackTokenService.validate(token, manifestId);
        VideoManifestResponse manifest = streamingService.getManifestById(manifestId);
        String playlist = hlsManifestService.buildMasterPlaylist(manifest, token);
        return playlistResponse(playlist);
    }

    @Operation(summary = "Playlist một rendition — segment được viết lại thành presigned GET URL trỏ thẳng storage")
    @GetMapping(value = "/{manifestId}/{label}.m3u8")
    public ResponseEntity<String> renditionPlaylist(
            @PathVariable String manifestId, @PathVariable String label, @RequestParam("pt") String token) {
        Claims claims = playbackTokenService.validate(token, manifestId);
        VideoManifestResponse manifest = streamingService.getManifestById(manifestId);
        Duration segmentTtl = capped(playbackTokenService.remainingValidity(claims));
        String playlist = hlsManifestService.buildRenditionPlaylist(manifest, label, token, segmentTtl);
        return playlistResponse(playlist);
    }

    @Operation(summary = "Khoá AES-128 giải mã segment — chỉ phát khi token hợp lệ, không bao giờ cache")
    @GetMapping(value = "/{manifestId}/key")
    public ResponseEntity<byte[]> encryptionKey(@PathVariable String manifestId, @RequestParam("pt") String token) {
        playbackTokenService.validate(token, manifestId);
        VideoManifestResponse manifest = streamingService.getManifestById(manifestId);
        byte[] rawKey = keyUnwrapService.unwrap(manifest.encryptionKeyCiphertext());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(rawKey);
    }

    private ResponseEntity<String> playlistResponse(String body) {
        return ResponseEntity.ok()
                .contentType(HLS_MEDIA_TYPE)
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    private Duration capped(Duration remaining) {
        return remaining.compareTo(MAX_SEGMENT_URL_TTL) > 0 ? MAX_SEGMENT_URL_TTL : remaining;
    }
}

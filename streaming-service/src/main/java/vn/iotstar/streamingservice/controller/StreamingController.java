package vn.iotstar.streamingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.streamingservice.common.GenericResponse;
import vn.iotstar.streamingservice.model.dto.WatchProgressRequest;
import vn.iotstar.streamingservice.service.StreamingService;

@RestController
@RequestMapping("/api/v1/streaming")
@Tag(name = "Streaming API", description = "Phân giải phát video, tiến độ xem, lượt xem — mọi endpoint đòi hỏi đăng nhập")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;

    @Operation(summary = "Phân giải phim/tập thành URL master playlist đã gắn playback token")
    @GetMapping("/{idOrSlug}/manifest")
    public ResponseEntity<GenericResponse> manifest(
            @PathVariable String idOrSlug,
            @RequestParam(required = false) Integer episode,
            Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.success(
                streamingService.resolvePlayback(idOrSlug, episode, authentication.getName())));
    }

    @Operation(summary = "Cập nhật vị trí xem dở")
    @PutMapping("/{idOrSlug}/progress")
    public ResponseEntity<GenericResponse> updateProgress(
            @PathVariable String idOrSlug,
            @Valid @RequestBody WatchProgressRequest request,
            Authentication authentication) {
        streamingService.upsertProgress(authentication.getName(), idOrSlug, request);
        return ResponseEntity.ok(GenericResponse.ok("Progress saved"));
    }

    @Operation(summary = "Danh sách phim đang xem dở, mới nhất trước")
    @GetMapping("/continue-watching")
    public ResponseEntity<GenericResponse> continueWatching(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.success(
                streamingService.continueWatching(authentication.getName(), PageRequest.of(page, size))));
    }

    @Operation(summary = "Ghi nhận một lượt xem (dedup theo user trong một cửa sổ ngắn)")
    @PostMapping("/{idOrSlug}/view")
    public ResponseEntity<GenericResponse> recordView(
            @PathVariable String idOrSlug, Authentication authentication) {
        streamingService.recordView(authentication.getName(), idOrSlug);
        return ResponseEntity.ok(GenericResponse.ok("View recorded"));
    }
}

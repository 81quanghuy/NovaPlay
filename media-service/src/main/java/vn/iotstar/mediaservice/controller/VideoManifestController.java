package vn.iotstar.mediaservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.mediaservice.common.GenericResponse;
import vn.iotstar.mediaservice.common.dto.VideoManifestCompleteRequest;
import vn.iotstar.mediaservice.common.dto.VideoManifestFailRequest;
import vn.iotstar.mediaservice.service.VideoManifestService;

/**
 * Điều khiển vòng đời {@code VideoManifest}. Cố ý KHÔNG owner-gate như {@code MediaController}:
 * streaming-service phải phân giải manifest của bất kỳ phim đã publish nào, bất kể ai upload —
 * chỉ {@code ROLE_ADMIN}/{@code ROLE_SERVICE} mới gọi được, không có endpoint public nào ở đây.
 */
@RestController
@RequestMapping("/api/v1/media/video-manifests")
@Tag(name = "Video Manifest API", description = "Vòng đời transcode HLS: QUEUED -> PROCESSING -> READY|FAILED")
@RequiredArgsConstructor
public class VideoManifestController {

    private final VideoManifestService videoManifestService;

    @Operation(summary = "Chi tiết manifest theo id — streaming-service/transcoding-worker dùng")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public ResponseEntity<GenericResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.getById(id)));
    }

    @Operation(summary = "Manifest theo id của Media nguồn — streaming-service dùng để phân giải phim -> manifest")
    @GetMapping("/by-source/{sourceMediaId}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public ResponseEntity<GenericResponse> getBySourceMediaId(@PathVariable String sourceMediaId) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.getBySourceMediaId(sourceMediaId)));
    }

    @Operation(summary = "[SERVICE] transcoding-worker báo đã bắt đầu xử lý")
    @PatchMapping("/{id}/processing")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<GenericResponse> markProcessing(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.markProcessing(id)));
    }

    @Operation(summary = "[SERVICE] transcoding-worker báo hoàn tất, kèm renditions")
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<GenericResponse> markComplete(
            @PathVariable String id, @Valid @RequestBody VideoManifestCompleteRequest request) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.markComplete(id, request)));
    }

    @Operation(summary = "[SERVICE] transcoding-worker báo lỗi")
    @PatchMapping("/{id}/fail")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<GenericResponse> markFailed(
            @PathVariable String id, @Valid @RequestBody VideoManifestFailRequest request) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.markFailed(id, request.reason())));
    }

    @Operation(summary = "[ADMIN] Đẩy lại việc transcode cho một manifest đang FAILED")
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse> retry(@PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(videoManifestService.retry(id)));
    }
}

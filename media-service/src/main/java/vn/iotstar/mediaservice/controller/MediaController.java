package vn.iotstar.mediaservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.mediaservice.common.GenericResponse;
import vn.iotstar.mediaservice.common.dto.AbortMultipartRequest;
import vn.iotstar.mediaservice.common.dto.CompleteMultipartRequest;
import vn.iotstar.mediaservice.common.dto.MultipartInitRequest;
import vn.iotstar.mediaservice.common.dto.MultipartInitResponse;
import vn.iotstar.mediaservice.common.dto.PartUrlResponse;
import vn.iotstar.mediaservice.common.dto.UploadRequestDto;
import vn.iotstar.mediaservice.common.dto.UploadResponseDto;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media API", description = "Endpoints for media upload and management")
@RequiredArgsConstructor
public class MediaController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final MediaService mediaService;

    /**
     * Endpoint hiện có, giữ nguyên hình dạng: trả thẳng {@link UploadResponseDto} (không bọc
     * {@link GenericResponse}) vì {@code user-service}'s {@code MediaServiceClient} phụ thuộc vào
     * đúng shape này.
     */
    @Operation(summary = "Request upload URL from S3")
    @PostMapping("/upload/request")
    public UploadResponseDto requestUploadUrl(@RequestBody UploadRequestDto request) {
        return mediaService.requestUploadUrl(request);
    }

    @Operation(summary = "[Video] Khởi tạo multipart upload — bắt buộc cho file lớn hơn ngưỡng single-PUT")
    @PostMapping("/upload/multipart/init")
    public MultipartInitResponse initiateMultipartUpload(@RequestBody MultipartInitRequest request) {
        return mediaService.initiateMultipartUpload(request);
    }

    @Operation(summary = "[Video] Presigned PUT URL cho một phần cụ thể của multipart upload")
    @PostMapping("/upload/multipart/part-url")
    public PartUrlResponse presignUploadPart(
            @RequestParam String mediaId, @RequestParam String uploadId, @RequestParam int partNumber) {
        return mediaService.presignUploadPart(mediaId, uploadId, partNumber);
    }

    @Operation(summary = "[Video] Hoàn tất multipart upload sau khi mọi phần đã PUT xong")
    @PostMapping("/upload/multipart/complete")
    public ResponseEntity<GenericResponse> completeMultipartUpload(@Valid @RequestBody CompleteMultipartRequest request) {
        mediaService.completeMultipartUpload(request);
        return ResponseEntity.ok(GenericResponse.ok("Multipart upload completed"));
    }

    @Operation(summary = "[Video] Huỷ multipart upload dở dang")
    @PostMapping("/upload/multipart/abort")
    public ResponseEntity<GenericResponse> abortMultipartUpload(@Valid @RequestBody AbortMultipartRequest request) {
        mediaService.abortMultipartUpload(request.mediaId(), request.uploadId());
        return ResponseEntity.ok(GenericResponse.ok("Multipart upload aborted"));
    }

    @Operation(summary = "Media của người dùng hiện tại, có phân trang")
    @GetMapping("/me")
    public ResponseEntity<GenericResponse> getMyMedia(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Media> result = mediaService.getMyMedia(authentication.getName(), PageRequest.of(page, size));
        return ResponseEntity.ok(GenericResponse.success(result));
    }

    @Operation(summary = "Chi tiết một media theo id; chủ sở hữu hoặc ROLE_ADMIN mới xem được")
    @GetMapping("/{id}")
    public ResponseEntity<GenericResponse> getMediaById(
            @PathVariable String id,
            Authentication authentication) {
        Media media = mediaService.getMediaById(id, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(GenericResponse.success(media));
    }

    /**
     * Tách riêng khỏi {@code /me} để tránh IDOR: endpoint này cho phép tra cứu media của BẤT KỲ
     * owner nào qua {@code ownerId}, nên chỉ {@code ROLE_ADMIN} mới được gọi. Phân quyền hai lớp:
     * {@code SecurityConfig}'s {@code ADMIN_ONLY_ENDPOINTS} chặn ở tầng filter, {@code @PreAuthorize}
     * chặn lại ở tầng controller — mirror {@code MovieController}.
     */
    @Operation(summary = "[ADMIN] Media của một owner cụ thể, có phân trang")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse> getMediaByOwnerId(
            @RequestParam String ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Media> result = mediaService.getMediaByOwnerId(ownerId, PageRequest.of(page, size));
        return ResponseEntity.ok(GenericResponse.success(result));
    }

    @Operation(summary = "Xoá media: soft-delete record + hard-delete object S3 ngay lập tức (không thu hồi được)")
    @DeleteMapping("/{id}")
    public ResponseEntity<GenericResponse> deleteMedia(
            @PathVariable String id,
            Authentication authentication) {
        mediaService.deleteMedia(id, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(GenericResponse.ok("Media deleted"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}

package vn.iotstar.userservice.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

import java.time.Instant;

import static vn.iotstar.userservice.util.Constants.X_USER_EMAIL;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile API", description = "Quản lý hồ sơ người dùng")
public class UserController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Lấy hồ sơ người dùng hiện tại", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping("/me")
    public ResponseEntity<GenericResponse> getProfile(@RequestHeader(X_USER_EMAIL) String email) {
        UserProfileDTO profile = userProfileService.getProfile(email);
        return ResponseEntity.ok(GenericResponse.success(profile, "Profile retrieved successfully"));
    }

    @Operation(summary = "Cập nhật hồ sơ người dùng hiện tại", security = @SecurityRequirement(name = "bearer-jwt"))
    @PutMapping("/me")
    public ResponseEntity<GenericResponse> updateProfile(
            @RequestHeader(X_USER_EMAIL) String email,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfileDTO updated = userProfileService.updateProfile(email, request);
        return ResponseEntity.ok(GenericResponse.success(updated, "Profile updated successfully"));
    }

    @Operation(summary = "Yêu cầu upload avatar mới", security = @SecurityRequirement(name = "bearer-jwt"))
    @CircuitBreaker(name = "mediaService", fallbackMethod = "fallbackForRequestUpload")
    @PostMapping("/avatar/request-upload")
    public ResponseEntity<GenericResponse> changeAvatar(
            @RequestHeader(X_USER_EMAIL) String email,
            @Valid @RequestBody UploadRequestDto request) {
        String traceId = MDC.get("traceId");
        UploadResponseDto uploadResponseDto = userProfileService.changeAvatar(request, email, traceId);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message("Avatar change initiated successfully")
                .result(uploadResponseDto)
                .statusCode(HttpStatus.OK.value())
                .build());
    }

    /**
     * Trả về response 503 thay vì ném exception: ném từ trong fallback khiến chính lời gọi
     * fallback bị tính là thất bại và không hề làm dịu sự cố, chỉ đổi nhãn lỗi.
     */
    private ResponseEntity<GenericResponse> fallbackForRequestUpload(String email,
                                                                     UploadRequestDto request,
                                                                     Throwable ex) {
        log.warn("Media service unavailable while requesting avatar upload for {}: {}",
                email, ex.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GenericResponse.builder()
                        .success(false)
                        .message("Media service is currently unavailable. Please try again later.")
                        .statusCode(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .timestamp(Instant.now())
                        .build());
    }
}

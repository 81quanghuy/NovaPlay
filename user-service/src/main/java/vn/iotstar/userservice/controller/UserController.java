package vn.iotstar.userservice.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile API", description = "Quản lý hồ sơ người dùng")
public class UserController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Lấy hồ sơ người dùng hiện tại", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping("/me")
    public ResponseEntity<GenericResponse> getProfile(@RequestHeader("X-User-Email") String email) {
        UserProfileDTO profile = userProfileService.getProfile(email);
        return ResponseEntity.ok(GenericResponse.success(profile, "Profile retrieved successfully"));
    }

    @Operation(summary = "Cập nhật hồ sơ người dùng hiện tại", security = @SecurityRequirement(name = "bearer-jwt"))
    @PutMapping("/me")
    public ResponseEntity<GenericResponse> updateProfile(
            @RequestHeader("X-User-Email") String email,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfileDTO updated = userProfileService.updateProfile(email, request);
        return ResponseEntity.ok(GenericResponse.success(updated, "Profile updated successfully"));
    }

    @Operation(summary = "Yêu cầu upload avatar mới", security = @SecurityRequirement(name = "bearer-jwt"))
    @CircuitBreaker(name = "mediaService", fallbackMethod = "fallbackForRequestUpload")
    @PostMapping("/avatar/request-upload")
    public ResponseEntity<GenericResponse> changeAvatar(
            @RequestHeader("X-User-Email") String email,
            @RequestBody UploadRequestDto request) {
        String traceId = MDC.get("traceId");
        UploadResponseDto uploadResponseDto = userProfileService.changeAvatar(request, email, traceId);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message("Avatar change initiated successfully")
                .result(uploadResponseDto)
                .build());
    }

    private ResponseEntity<GenericResponse> fallbackForRequestUpload(String email, UploadRequestDto request, Exception ex) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Media service is currently unavailable. Please try again later.");
    }
}

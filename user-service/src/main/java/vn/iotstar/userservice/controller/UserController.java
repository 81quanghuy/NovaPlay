package vn.iotstar.userservice.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Authentication API", description = "Endpoints for user registration, login, logout, and token refresh")
public class UserController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Change avatar of user")
    @CircuitBreaker(name = "mediaService", fallbackMethod = "fallbackForRequestUpload")
    @PostMapping("/avatar/request-upload")
    public ResponseEntity<GenericResponse> changeAvatar(@AuthenticationPrincipal Jwt jwt, @RequestBody UploadRequestDto request) {
        String traceId = MDC.get("traceId");
        String email = jwt.getSubject();
        UploadResponseDto uploadResponseDto  =userProfileService.changeAvatar(request, email, traceId);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message("Avatar change initiated successfully")
                .result(uploadResponseDto)
                .build());
    }

    private UploadResponseDto fallbackForRequestUpload() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Media service is currently unavailable. Please try again later.");
    }
}

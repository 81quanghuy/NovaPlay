package vn.iotstar.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.authservice.mapper.UserMapper;
import vn.iotstar.authservice.model.dto.*;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.service.AuthService;
import vn.iotstar.authservice.service.JwtService;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.service.TokenBlacklist;
import vn.iotstar.utils.constants.GenericResponse;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for user registration, login, logout, and token refresh")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<GenericResponse> register(@Valid @RequestBody UserCreationRequest request) {
        UserResponse registeredUser = authService.register(request);
        otpService.generateAndDispatch(registeredUser.id(), registeredUser.email(), request.locale(), MDC.get("traceId"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.success(registeredUser, "User registered successfully. Check your email for OTP.", HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Verify OTP to activate user account")
    @PostMapping("/verify-otp")
    public ResponseEntity<GenericResponse> verify(@Valid @RequestBody VerifyOtpRequest req) {
        boolean isValid = otpService.verify(req.email(), req.otp());
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    GenericResponse.builder()
                            .success(false)
                            .message("Invalid OTP")
                            .statusCode(HttpStatus.BAD_REQUEST.value())
                            .build()
            );
        }
        authService.activateAccount(req.email());
        return ResponseEntity.ok(GenericResponse.ok("OTP verified successfully. Account activated."));
    }

    @Operation(summary = "Resend OTP for account activation",
            description = "Resends the OTP to the user's email if they haven't verified it yet.")
    @PostMapping("/resend-registration-otp")
    public ResponseEntity<GenericResponse> resendRegistrationOtp(@Valid @RequestBody EmailRequest emailRequest) {
        authService.resendRegistrationOtp(emailRequest, MDC.get("traceId"));
        return ResponseEntity.ok(GenericResponse.ok("OTP resent successfully. Please check your email."));
    }

    @Operation(summary = "Authenticate a user and get tokens")
    @PostMapping("/login")
    public ResponseEntity<GenericResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(GenericResponse.success(authResponse, "Login successful"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "Refresh the access token",
            description = "Use the refresh token to obtain a new access token.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/refresh-token")
    public ResponseEntity<GenericResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(GenericResponse.success(authResponse, "Token refreshed successfully"));
    }

    @Operation(
            summary = "Revoke refresh token (logout)",
            description = "Thu hồi refresh token theo RFC 7009. Idempotent: gọi nhiều lần vẫn 204.",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request,
                                       @AuthenticationPrincipal User user,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(request.refreshToken(), user.getEmail());
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                String jti = jwtService.extractJti(accessToken);
                Date exp = jwtService.extractExpiration(accessToken);
                tokenBlacklist.revoke(jti, exp);
            } catch (Exception ignored) {
                // token already invalid — blacklisting not needed
            }
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send OTP to user's email for password reset",
            description = "This endpoint sends a One-Time Password (OTP) to the user's registered email address.")
    @PostMapping("/forgot-password")
    public ResponseEntity<GenericResponse> forgotPassword(@Valid @RequestBody EmailRequest emailRequest) {
        authService.forgotPassword(emailRequest, MDC.get("traceId"));
        return ResponseEntity.ok(GenericResponse.ok("OTP sent to email successfully"));
    }

    @Operation(summary = "Reset password using OTP",
            description = "This endpoint allows the user to reset their password using the OTP sent to their email.")
    @PostMapping("/reset-password")
    public ResponseEntity<GenericResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        authService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok(GenericResponse.ok("Password reset successfully"));
    }

    @Operation(summary = "Change user password",
            description = "This endpoint allows the user to change their password using their current password.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                                          @AuthenticationPrincipal User user) {
        authService.changePassword(changePasswordRequest, user.getEmail());
        return ResponseEntity.ok(GenericResponse.ok("Password changed successfully"));
    }

    @Operation(summary = "Get current authenticated user profile",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse> me(@AuthenticationPrincipal User user) {
        UserResponse profile = UserMapper.toUserResponse(user);
        return ResponseEntity.ok(GenericResponse.success(profile, "User profile retrieved"));
    }

    @Operation(summary = "Introspect token for service-to-service use",
            description = "Returns active status, subject, roles, and expiration for a given token.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/introspect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse> introspect(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        boolean active = jwtService.isTokenValid(token);
        Map<String, Object> introspection = Map.of(
                "active", active,
                "sub", active ? jwtService.extractEmail(token) : "",
                "roles", active ? jwtService.extractRoles(token) : java.util.List.of(),
                "jti", active ? jwtService.extractJti(token) : ""
        );
        return ResponseEntity.ok(GenericResponse.success(introspection, "Token introspection result"));
    }
}

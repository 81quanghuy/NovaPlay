package vn.iotstar.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.authservice.model.dto.*;
import vn.iotstar.authservice.service.IAuthService;
import vn.iotstar.utils.constants.GenericResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for user registration, login, logout, and token refresh")
public class AuthController {

    private final IAuthService authService;

    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserCreationRequest request) {
        UserResponse registeredUser = authService.register(request);
        return new ResponseEntity<>(registeredUser, HttpStatus.CREATED);
    }

    @Operation(summary = "Authenticate a user and get tokens")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Refresh the access token",
    description = "Use the refresh token to obtain a new access token. " +
            "This endpoint is idempotent: calling it multiple times will return the same response as long as the refresh token is valid.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping("/refresh-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        AuthResponse authResponse = authService.refreshToken(request.refreshToken(), jwt.getSubject());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(
            summary = "Revoke refresh token (logout)",
            description = "Thu hồi refresh token theo RFC 7009. Idempotent: gọi nhiều lần vẫn 204.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        authService.logout(request.refreshToken(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send OTP to user's email for password reset",
            description = "This endpoint sends a One-Time Password (OTP) to the user's registered email address for password reset purposes.")
    @PostMapping("/forgot-password")
    public ResponseEntity<GenericResponse> forgotPassword(@Valid @RequestBody EmailRequest emailRequest) {
        authService.forgotPassword(emailRequest);
        return ResponseEntity.ok(new GenericResponse(true,
                "OTP sent successfully",
                null,
                HttpStatus.OK.value()));
    }

    @Operation(summary = "Reset password using OTP",
            description = "This endpoint allows the user to reset their password using the OTP sent to their email.")
    @PostMapping("/reset-password")
    public ResponseEntity<GenericResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        authService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok(new GenericResponse(true,
                "Password reset successfully",
                null,
                HttpStatus.OK.value()));
    }

    @Operation(summary = "Change user password",
            description = "This endpoint allows the user to change their password using their current password.")
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                                           @AuthenticationPrincipal Jwt jwt) {
        authService.changePassword(changePasswordRequest, jwt.getSubject());
        return ResponseEntity.ok(new GenericResponse(true,
                "Password changed successfully",
                null,
                HttpStatus.OK.value()));
    }


}
package vn.iotstar.authservice.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "DTO chứa refresh token để yêu cầu access token mới hoặc để đăng xuất")
public record RefreshTokenRequest (
        @Schema(description = "Refresh Token hợp lệ của người dùng.",
                example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
        @NotBlank(message = "Refresh token không được để trống")
        @JsonProperty("refresh_token")
        String refreshToken
){}
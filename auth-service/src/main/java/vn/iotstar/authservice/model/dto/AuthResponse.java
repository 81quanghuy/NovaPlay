package vn.iotstar.authservice.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "AuthResponse", description = "DTO chứa kết quả trả về sau khi xác thực thành công")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @Schema(description = "Access Token dùng để xác thực các yêu cầu API.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIi...")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "Refresh Token dùng để lấy Access Token mới khi hết hạn.",
            example = "a1b2c3d4-e5f6-...")
    @JsonProperty("refresh_token")
    private String refreshToken;

    @Schema(description = "Loại token, mặc định là 'Bearer'.",
            example = "Bearer")
    @Builder.Default
    @JsonProperty("token_type")
    private String tokenType = "Bearer";

    @Schema(description = "Thời gian sống của Access Token (tính bằng giây).",
            example = "3600")
    @JsonProperty("expires_in")
    private long expiresIn;

    @Schema(description = "Thông tin hồ sơ của người dùng đã xác thực.")
    @JsonProperty("user_profile")
    private UserResponse userProfile; // Sử dụng lại UserResponse đã tạo trước đó
}
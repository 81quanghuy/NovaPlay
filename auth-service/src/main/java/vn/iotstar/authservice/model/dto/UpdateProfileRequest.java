package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest", description = "DTO chứa thông tin cập nhật hồ sơ người dùng")
public record UpdateProfileRequest(
        @Schema(description = "Tên đăng nhập mới", example = "quanghuy")
        @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
        String username
) {}

package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "DTO chứa thông tin cần thiết cho việc đăng nhập")
public record LoginRequest(

        @Schema(description = "Tên đăng nhập của người dùng.",
                example = "john_doe")
        @NotBlank(message = "Tên đăng nhập không được để trống")
        String username,

        @Schema(description = "Mật khẩu của người dùng.",
                example = "yourStrongPassword123")
        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {}
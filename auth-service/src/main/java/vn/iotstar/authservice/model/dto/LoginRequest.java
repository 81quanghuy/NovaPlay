package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "DTO chứa thông tin cần thiết cho việc đăng nhập")
public record LoginRequest(

        @Schema(description = "Email hoặc tên đăng nhập của người dùng.",
                example = "ngoquanghuy@gmail.com")
        @NotBlank(message = "Email hoặc tên đăng nhập không được để trống")
        String emailOrUsername,

        @Schema(description = "Mật khẩu của người dùng.",
                example = "yourStrongPassword123")
        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {}
package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "DTO chứa thông tin cần thiết cho việc đăng nhập")
public record LoginRequest(

        @Schema(description = "Địa chỉ email của người dùng.",
                example = "quanghuy@gmail.com")
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @Schema(description = "Mật khẩu của người dùng.",
                example = "yourStrongPassword123")
        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {}
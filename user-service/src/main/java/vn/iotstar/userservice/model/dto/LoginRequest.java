package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest",
        description = "Yêu cầu đăng nhập với email và mật khẩu.")
public record LoginRequest (
        @Schema(description = "Email của người dùng (case-insensitive).",
                example = "user@gmail.com")
        @Email
        @NotBlank
        String email,

        @Schema(description = "Mật khẩu của người dùng.",
                example = "P@ssw0rd")
        @NotBlank
        String password
){}

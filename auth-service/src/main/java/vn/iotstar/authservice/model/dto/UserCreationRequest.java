package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.iotstar.authservice.util.validation.StrongPassword;

@Schema(name = "UserCreationRequest", description = "DTO chứa thông tin để tạo người dùng mới")
public record UserCreationRequest(
        @Schema(description = "Tên đăng nhập", example = "quanghuy")
        @NotBlank(message = "Username không được để trống")
        @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
        String username,

        @Schema(description = "Địa chỉ email", example = "quanghuy@gmail.com")
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @Schema(description = "Mật khẩu", example = "yourStrongPassword123!")
        @NotBlank(message = "Mật khẩu không được để trống")
        @StrongPassword
        String password,

        @Schema(description = "Ngôn ngữ của người dùng", example = "en")
        String locale
) {}
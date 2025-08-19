package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Schema(name = "UserResponse", description = "DTO chứa thông tin người dùng trả về cho client")
public record UserResponse(
        @Schema(description = "ID của người dùng")
        UUID id,

        @Schema(description = "Tên đăng nhập")
        String username,

        @Schema(description = "Địa chỉ email")
        String email,

        @Schema(description = "Trạng thái hoạt động")
        Boolean isActive,

        @Schema(description = "Trạng thái xác thực email")
        boolean isEmailVerified,

        @Schema(description = "Lần đăng nhập cuối")
        Date lastLoginAt,

        @Schema(description = "Danh sách vai trò của người dùng")
        Set<RoleDTO> roles
) {}
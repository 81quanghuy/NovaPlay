package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.iotstar.authservice.util.RoleName;

import java.util.Set;
import java.util.UUID;

@Schema(name = "RoleDTO", description = "DTO cho vai trò người dùng")
public record RoleDTO(
        @Schema(description = "ID của vai trò", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        UUID id,

        @Schema(description = "Tên của vai trò", example = "ADMIN")
        RoleName roleName,

        @Schema(description = "Mô tả chi tiết về vai trò", example = "Quản trị viên hệ thống")
        String description,

        @Schema(description = "Danh sách các quyền của vai trò")
        Set<PermissionDTO> permissions
) {}
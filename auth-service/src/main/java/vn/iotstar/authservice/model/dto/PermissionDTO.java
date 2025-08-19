package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PermissionDTO", description = "DTO cho quyền hạn trong hệ thống")
public record PermissionDTO(
        @Schema(description = "ID của quyền", example = "1")
        Integer id,

        @Schema(description = "Tên định danh của quyền", example = "movie:read")
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(description = "Mô tả chi tiết về quyền", example = "Quyền xem thông tin phim")
        @Size(max = 255)
        String description
) {}
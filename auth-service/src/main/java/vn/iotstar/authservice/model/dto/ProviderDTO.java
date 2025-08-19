package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ProviderDTO", description = "DTO cho liên kết nhà cung cấp xác thực")
public record ProviderDTO(
        @Schema(description = "ID của liên kết provider")
        String id,

        @Schema(description = "Tên nhà cung cấp", example = "GOOGLE")
        String providerName,

        @Schema(description = "ID của người dùng trong hệ thống của bạn")
        UUID userId
) {}
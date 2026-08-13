package vn.iotstar.configservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "ConfigFlagResponse", description = "Cặp key/value cấu hình động")
public record ConfigFlagDto(
        @Schema(example = "media-storage-provider") String key,
        @Schema(example = "aws-s3") String value) {
}

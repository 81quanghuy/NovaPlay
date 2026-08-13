package vn.iotstar.mediaservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(title = "VideoManifestCompleteRequest", description = "transcoding-worker báo hoàn tất.")
public record VideoManifestCompleteRequest(
        @NotBlank String masterManifestS3Key,
        @NotNull Integer durationSeconds,
        @NotEmpty List<@Valid RenditionRequest> renditions,
        @NotBlank @Schema(description = "Khoá AES-128 đã bọc (AES-GCM, base64) bằng TRANSCODE_KEY_WRAP_SECRET")
        String wrappedKey
) {
}

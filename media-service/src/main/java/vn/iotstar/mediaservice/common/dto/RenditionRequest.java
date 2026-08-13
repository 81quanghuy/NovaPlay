package vn.iotstar.mediaservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(title = "RenditionRequest", description = "Một rendition HLS đã transcode xong.")
public record RenditionRequest(
        @NotBlank String resolutionLabel,
        @NotNull Integer width,
        @NotNull Integer height,
        @NotNull Integer bitrateKbps,
        @NotBlank String playlistS3Key
) {
}

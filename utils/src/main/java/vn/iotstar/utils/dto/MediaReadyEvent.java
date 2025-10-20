package vn.iotstar.utils.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        title = "MediaReadyEvent",
        description = "Event object indicating that media is ready.",
        requiredMode = Schema.RequiredMode.REQUIRED
)
public record MediaReadyEvent(
        @Schema(description = "ID of the media that is ready", example = "media123")
        String mediaId,
        @Schema(description = "ID of the owner of the media", example = "user123")
        String ownerId,
        @Schema(description = "CDN URL where the media can be accessed",
                example = "https://cdn.example.com/media123")
        String cdnUrl
){
}

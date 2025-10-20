package vn.iotstar.utils.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(title = "UploadResponse",
        description = "Response containing media ID and presigned URL for upload")
public record UploadResponseDto (
        @Schema(description = "Unique identifier for the media",
                example = "64a7f0c8e4b0f5d6c8e4b0f5")
        @NotBlank(message = "Media ID must not be blank")
        String mediaId,

        @Schema(description = "Presigned URL for uploading the media",
                example = "https://s3.amazonaws.com/your-bucket/users/123/media/64a7f0c8")
        @NotBlank(message = "Presigned URL must not be blank")
        String presignedUrl)
{}

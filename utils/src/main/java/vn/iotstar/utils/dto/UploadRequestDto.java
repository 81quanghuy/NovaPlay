package vn.iotstar.utils.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        title = "UploadRequestDto",
        description = "Request object for uploading media files.",
        requiredMode = Schema.RequiredMode.REQUIRED)
public record UploadRequestDto(

        @Schema(description = "ID of the owner uploading the file", example = "12345")
        String ownerId,

        @Schema(description = "Original name of the file being uploaded", example = "photo.jpg")
        String fileName,

        @Schema(description = "MIME type of the file being uploaded", example = "image/jpeg")
        String contentType,

        @Schema(description = "Size of the file in bytes", example = "204800")
        Long size
) {
}

package vn.iotstar.mediaservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "MultipartInitResponse")
public record MultipartInitResponse(
        String mediaId,
        String uploadId,
        @Schema(description = "Số phần được đề xuất, dùng để client tự chia file") int recommendedPartCount,
        @Schema(description = "Kích thước mỗi phần khuyến nghị, bytes (trừ phần cuối)") long recommendedPartSizeBytes
) {
}

package vn.iotstar.mediaservice.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "MultipartInitRequest", description = "Khởi tạo upload nhiều phần cho file lớn (video).")
public record MultipartInitRequest(
        String ownerId,
        String fileName,
        String contentType,
        @Schema(description = "Tổng kích thước file, bytes") Long size
) {
}

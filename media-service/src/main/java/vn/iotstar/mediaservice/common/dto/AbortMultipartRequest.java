package vn.iotstar.mediaservice.common.dto;

import jakarta.validation.constraints.NotBlank;

public record AbortMultipartRequest(@NotBlank String mediaId, @NotBlank String uploadId) {
}

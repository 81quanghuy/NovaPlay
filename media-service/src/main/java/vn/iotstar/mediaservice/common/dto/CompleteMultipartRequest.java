package vn.iotstar.mediaservice.common.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CompleteMultipartRequest(
        @NotBlank String mediaId,
        @NotBlank String uploadId,
        @NotEmpty List<@Valid CompletedPartDto> parts
) {
}

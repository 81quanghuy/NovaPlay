package vn.iotstar.mediaservice.common.dto;

import jakarta.validation.constraints.NotBlank;

public record VideoManifestFailRequest(@NotBlank String reason) {
}

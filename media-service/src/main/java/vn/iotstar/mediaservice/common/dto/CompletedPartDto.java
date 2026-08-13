package vn.iotstar.mediaservice.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** ETag trả về từ S3 sau mỗi lần PUT một part — client phải giữ lại để gửi kèm complete. */
public record CompletedPartDto(@NotNull Integer partNumber, @NotBlank String eTag) {
}

package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "UpsertWatchProgressRequest",
        description = "Cập nhật/ghi nhận tiến độ xem để hỗ trợ 'Tiếp tục xem'.")
public record UpsertWatchProgressRequest(

        @Schema(description = "Mã nội dung đang xem (movie hoặc episode).",
                example = "EP_203", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9:_\\-\\.]+$", message = "movieId chỉ gồm chữ/số/:-_.")
        String movieId,

        @Schema(description = "ID series (nếu là tập thuộc series).",
                example = "SER_99")
        @Size(max = 64)
        String seriesId,

        @Schema(description = "ID season (nếu là tập thuộc series).",
                example = "S2")
        @Size(max = 64)
        String seasonId,

        @Schema(description = "ID episode (nếu là tập thuộc series).",
                example = "EP_203")
        @Size(max = 64)
        String episodeId,

        @Schema(description = "Vị trí hiện tại (ms).",
                example = "1234567", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero
        Long positionMs,

        @Schema(description = "Tổng thời lượng nội dung (ms). Nếu không biết có thể để trống.",
                example = "5400000", minimum = "1")
        @Positive
        Long durationMs,

        @Schema(description = "Client báo đã xem xong (server vẫn tự tính completed).",
                example = "false")
        Boolean ended
) {
    @AssertTrue(message = "positionMs phải <= durationMs khi có durationMs")
    public boolean isPositionNotAfterDuration() {
        return durationMs == null || positionMs == null || positionMs <= durationMs;
    }
}

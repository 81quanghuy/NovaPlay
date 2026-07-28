package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "WatchProgressDTO", description = "Tiến độ xem của một nội dung.")
public record WatchProgressDTO(

        @Schema(description = "Mã nội dung đang xem.", example = "EP_203")
        String movieId,

        @Schema(description = "ID series, nếu nội dung thuộc một series.", example = "SER_99")
        String seriesId,

        @Schema(description = "ID season, nếu nội dung thuộc một series.", example = "S2")
        String seasonId,

        @Schema(description = "ID episode, nếu nội dung thuộc một series.", example = "EP_203")
        String episodeId,

        @Schema(description = "Vị trí phát hiện tại tính bằng ms.", example = "1234567")
        long positionMs,

        @Schema(description = "Tổng thời lượng nội dung tính bằng ms.", example = "5400000")
        Long durationMs,

        @Schema(description = "Phần trăm đã xem, từ 0 đến 100.", example = "23")
        Integer percent,

        @Schema(description = "Nội dung đã được xem xong hay chưa.", example = "false")
        boolean completed,

        @Schema(description = "Lần cuối cùng tiến độ này được cập nhật.")
        Instant lastWatchedAt
) {}

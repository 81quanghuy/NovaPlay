package vn.iotstar.streamingservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ManifestResponseDto", description = "Thông tin phát cho một phim/tập — trỏ tới master playlist đã gắn playback token.")
public record ManifestResponseDto(
        String movieTitle,
        String manifestId,
        @Schema(description = "URL master playlist, đã gắn sẵn playback token — dùng thẳng làm src cho player.")
        String masterPlaylistUrl,
        Integer durationSeconds,
        @Schema(description = "Vị trí xem dở lần trước, giây. 0 nếu chưa xem.")
        int resumePositionSeconds
) {
}

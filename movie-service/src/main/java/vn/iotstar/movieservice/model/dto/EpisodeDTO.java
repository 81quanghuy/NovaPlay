package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(name = "EpisodeDTO", description = "Một tập của phim bộ.")
public record EpisodeDTO(

        @Schema(description = "Số thứ tự tập, bắt đầu từ 1.", example = "3")
        Integer episodeNumber,

        @Schema(description = "Tiêu đề tập.", example = "Bí mật bị chôn giấu")
        String title,

        @Schema(description = "Id đục trỏ vào media-service; dùng streaming-service để phân giải URL phát.")
        String mediaId,

        @Schema(description = "Thời lượng tập tính bằng phút.", example = "45")
        Integer durationInMinutes

) implements Serializable {}

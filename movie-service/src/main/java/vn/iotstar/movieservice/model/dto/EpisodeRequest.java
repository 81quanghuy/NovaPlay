package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "EpisodeRequest", description = "Một tập trong yêu cầu cập nhật danh sách tập.")
public record EpisodeRequest(

        @Schema(description = "Số thứ tự tập, bắt đầu từ 1.", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1)
        Integer episodeNumber,

        @Schema(description = "Tiêu đề tập.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 300)
        String title,

        @Schema(description = "URL video của tập.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1024)
        String videoUrl,

        @Schema(description = "Thời lượng tập tính bằng phút.", example = "45")
        @Min(1) @Max(1000)
        Integer durationInMinutes
) {}

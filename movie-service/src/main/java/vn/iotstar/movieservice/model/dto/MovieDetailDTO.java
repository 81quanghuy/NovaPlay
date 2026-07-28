package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.iotstar.movieservice.model.enums.MovieStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "MovieDetailDTO", description = "Phim đầy đủ, dùng cho trang chi tiết.")
public record MovieDetailDTO(

        String id,
        String slug,
        String title,
        String description,

        @Schema(description = "Ngày phát hành.", example = "2024-11-20")
        LocalDate releaseDate,

        @Schema(description = "Thời lượng phim lẻ tính bằng phút; null với phim bộ.", example = "128")
        Integer durationInMinutes,

        @Schema(description = "URL ảnh poster.", format = "uri")
        String posterUrl,

        @Schema(description = "Là phim bộ hay phim lẻ.", example = "true")
        boolean series,

        MovieStatus status,

        List<GenreDTO> genres,

        List<CastMemberDTO> cast,

        @Schema(description = "Danh sách tập, đã sắp theo số tập tăng dần. Rỗng với phim lẻ.")
        List<EpisodeDTO> episodes

) implements Serializable {}

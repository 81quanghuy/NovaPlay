package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.iotstar.movieservice.model.enums.MinPlan;
import vn.iotstar.movieservice.model.enums.MovieStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Hình dạng rút gọn cho danh sách duyệt catalog.
 * <p>
 * Cố tình không có {@code description}, {@code cast} và {@code episodes}: một trang 20 phim kèm
 * mô tả đầy đủ và toàn bộ danh sách tập sẽ phình payload lên nhiều lần mà giao diện lưới phim
 * không dùng tới.
 */
@Schema(name = "MovieSummaryDTO", description = "Phim ở dạng rút gọn, dùng cho danh sách.")
public record MovieSummaryDTO(

        String id,
        String slug,
        String title,

        @Schema(description = "Ngày phát hành.", example = "2024-11-20")
        LocalDate releaseDate,

        @Schema(description = "Thời lượng phim lẻ tính bằng phút; null với phim bộ.", example = "128")
        Integer durationInMinutes,

        @Schema(description = "URL ảnh poster.", format = "uri")
        String posterUrl,

        @Schema(description = "Là phim bộ hay phim lẻ.", example = "false")
        boolean series,

        @Schema(description = "Trạng thái hiển thị. Endpoint công khai luôn trả PUBLISHED.")
        MovieStatus status,

        @Schema(description = "Gói cước tối thiểu cần để xem.", example = "MEMBER")
        MinPlan minPlan,

        List<GenreDTO> genres

) implements Serializable {}

package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Dùng chung cho cả tạo và sửa phim.
 * <p>
 * Không nhận {@code status}: chuyển trạng thái đi qua endpoint riêng
 * {@code PATCH /api/v1/movies/{id}/status} để hành động publish luôn tường minh và ghi log được,
 * thay vì lẫn vào một lần cập nhật thông thường.
 * <p>
 * Cũng không nhận {@code slug}: slug được sinh từ tiêu đề để không bao giờ lệch với nội dung.
 */
@Schema(name = "MovieRequest", description = "Yêu cầu tạo hoặc cập nhật phim.")
public record MovieRequest(

        @Schema(description = "Tiêu đề phim.", example = "Nhà Bà Nữ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 300)
        String title,

        @Schema(description = "Mô tả nội dung.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 10_000)
        String description,

        @Schema(description = "Ngày phát hành.", example = "2024-11-20")
        LocalDate releaseDate,

        @Schema(description = "Thời lượng phim lẻ tính bằng phút. Bỏ trống với phim bộ.",
                example = "128")
        @Min(1) @Max(1000)
        Integer durationInMinutes,

        @Schema(description = "URL ảnh poster.")
        @Size(max = 1024)
        String posterUrl,

        @Schema(description = "Là phim bộ hay phim lẻ.", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean series,

        @Schema(description = "Danh sách mã thể loại. Tên thể loại được service tự điền.")
        @Size(max = 20, message = "Một phim không thể có quá 20 thể loại")
        List<@NotBlank @Size(max = 64) String> genreIds,

        @Schema(description = "Danh sách ê-kíp.")
        @Size(max = 200, message = "Danh sách ê-kíp không được quá 200 người")
        List<@Valid CastMemberRequest> cast,

        @Schema(description = "Danh sách tập; chỉ chấp nhận khi series=true.")
        @Size(max = 2000, message = "Một phim bộ không được quá 2000 tập")
        List<@Valid EpisodeRequest> episodes
) {}

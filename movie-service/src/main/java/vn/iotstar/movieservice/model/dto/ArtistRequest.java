package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ArtistRequest", description = "Yêu cầu tạo hoặc cập nhật nghệ sĩ.")
public record ArtistRequest(

        @Schema(description = "Họ tên đầy đủ.", example = "Trấn Thành",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200)
        String fullName,

        @Schema(description = "Tiểu sử ngắn.")
        @Size(max = 5000)
        String bio,

        @Schema(description = "URL ảnh đại diện.")
        @Size(max = 1024)
        String avatarUrl
) {}

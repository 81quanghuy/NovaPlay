package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "GenreRequest", description = "Yêu cầu tạo hoặc cập nhật thể loại.")
public record GenreRequest(

        @Schema(description = "Tên thể loại.", example = "Hành động",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        String name
) {}

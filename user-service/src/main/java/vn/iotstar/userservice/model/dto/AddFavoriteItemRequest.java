package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.iotstar.userservice.util.ContentType;

@Schema(name = "AddFavoriteItemRequest",
        description = "Thêm nội dung vào danh sách yêu thích (My List).")
public record AddFavoriteItemRequest(

        @Schema(description = "Mã nội dung (movie/episode/season/series).",
                example = "MOV_12345",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9:_\\-\\.]+$", message = "movieId chỉ gồm chữ/số/:-_.")
        String movieId,

        @Schema(description = "Loại nội dung.",
                example = "MOVIE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "movieType phải là MOVIE|EPISODE|SEASON|SERIES")
        ContentType movieType
) {}

package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.iotstar.userservice.util.ContentType;

import java.time.Instant;

@Schema(name = "FavoriteItemDTO", description = "Một mục trong danh sách yêu thích.")
public record FavoriteItemDTO(

        @Schema(description = "Mã nội dung.", example = "MOV_12345")
        String movieId,

        @Schema(description = "Loại nội dung.", example = "MOVIE")
        ContentType contentType,

        @Schema(description = "Thời điểm được thêm vào danh sách yêu thích.")
        Instant addedAt
) {}

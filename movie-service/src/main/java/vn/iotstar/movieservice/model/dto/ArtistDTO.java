package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(name = "ArtistDTO", description = "Một nghệ sĩ (diễn viên, đạo diễn, biên kịch...).")
public record ArtistDTO(

        @Schema(description = "Mã nghệ sĩ.")
        String id,

        @Schema(description = "Họ tên đầy đủ.", example = "Trấn Thành")
        String fullName,

        @Schema(description = "Tiểu sử ngắn.")
        String bio,

        @Schema(description = "URL ảnh đại diện.", format = "uri")
        String avatarUrl

) implements Serializable {}

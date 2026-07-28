package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(name = "CastMemberDTO", description = "Một dòng trong danh sách diễn viên/ê-kíp.")
public record CastMemberDTO(

        @Schema(description = "Mã nghệ sĩ, dùng để mở trang chi tiết nghệ sĩ.")
        String artistId,

        @Schema(description = "Họ tên nghệ sĩ.", example = "Trấn Thành")
        String fullName,

        @Schema(description = "Vai trò trong ê-kíp.", example = "ACTOR")
        String role,

        @Schema(description = "Tên nhân vật; chỉ có nghĩa với vai diễn viên.", example = "Ba Sang")
        String characterName

) implements Serializable {}

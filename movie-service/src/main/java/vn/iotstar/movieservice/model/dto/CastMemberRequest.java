package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CastMemberRequest", description = "Một dòng ê-kíp trong yêu cầu tạo/sửa phim.")
public record CastMemberRequest(

        @Schema(description = "Mã nghệ sĩ đã tồn tại trong collection artists.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64)
        String artistId,

        @Schema(description = "Vai trò trong ê-kíp.", example = "ACTOR",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 50)
        String role,

        @Schema(description = "Tên nhân vật; chỉ có nghĩa với vai diễn viên.", example = "Ba Sang")
        @Size(max = 200)
        String characterName
) {}

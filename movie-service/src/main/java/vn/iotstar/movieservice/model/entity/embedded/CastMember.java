package vn.iotstar.movieservice.model.entity.embedded;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.movieservice.utils.Constants;

import java.io.Serial;
import java.io.Serializable;

/**
 * Một dòng trong danh sách diễn viên/ê-kíp của phim.
 * <p>
 * {@code fullName} là bản sao từ collection {@code artists} để hiển thị chi tiết phim không phải
 * truy vấn thêm. {@code artistId} vẫn được giữ để trỏ về nghệ sĩ gốc và để fan-out cập nhật khi
 * tên nghệ sĩ thay đổi.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode
public class CastMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(Constants.CAST_ARTIST_ID)
    private String artistId;

    @Field(Constants.CAST_FULL_NAME)
    private String fullName;

    /** Vai trò trong ê-kíp: ACTOR, DIRECTOR, WRITER... để dạng chuỗi tự do cho linh hoạt. */
    @Field(Constants.CAST_ROLE)
    private String role;

    /** Tên nhân vật; chỉ có nghĩa khi role là diễn viên. */
    @Field(Constants.CAST_CHARACTER_NAME)
    private String characterName;
}

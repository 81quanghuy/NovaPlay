package vn.iotstar.movieservice.model.entity.embedded;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.movieservice.utils.Constants;

import java.io.Serial;
import java.io.Serializable;

/**
 * Bản sao thể loại được nhúng thẳng vào document phim.
 * <p>
 * Nhúng để đọc chi tiết phim chỉ tốn một truy vấn — MongoDB không có join rẻ. Cái giá phải trả là
 * khi đổi tên thể loại, {@code GenreService} phải fan-out cập nhật sang mọi phim đang tham chiếu.
 * Đổi tên thể loại rất hiếm nên đánh đổi này có lợi.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode
public class GenreRef implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(Constants.GENRE_REF_ID)
    private String id;

    @Field(Constants.GENRE_REF_NAME)
    private String name;
}

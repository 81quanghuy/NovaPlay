package vn.iotstar.movieservice.model.entity.embedded;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.movieservice.utils.Constants;

import java.io.Serial;
import java.io.Serializable;

/**
 * Một tập của phim bộ, nhúng trong document phim.
 * <p>
 * Nhúng thay vì tách collection riêng: một series 500 tập vẫn còn xa giới hạn 16MB của một
 * document, và cách này giúp lấy toàn bộ danh sách tập chỉ bằng truy vấn đã dùng để lấy phim.
 * Đánh đổi: sửa một tập sẽ ghi lại cả document — chấp nhận được vì catalog ghi rất thưa.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode
public class Episode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(Constants.EPISODE_NUMBER)
    private Integer episodeNumber;

    @Field(Constants.EPISODE_TITLE)
    private String title;

    /**
     * Id đục trỏ vào một asset trong media-service (Media/VideoManifest). Catalog không bao giờ
     * gọi sang media-service để xác thực giá trị này — chỉ streaming-service phân giải nó thành
     * URL phát được lúc xem, nên id treo (media chưa upload/bị xoá) chỉ lộ ra thành 404 khi phát,
     * không ảnh hưởng catalog. Vì là id đục, an toàn khi trả về qua endpoint public GET.
     */
    @Field(Constants.EPISODE_MEDIA_ID)
    private String mediaId;

    @Field(Constants.EPISODE_DURATION)
    private Integer durationInMinutes;
}

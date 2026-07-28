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
     * Nợ kỹ thuật đã biết: quyền sở hữu URL phát video thuộc về streaming-service. Catalog nên chỉ
     * giữ một asset id. Giữ nguyên ở đợt này để không mở rộng phạm vi.
     */
    @Field(Constants.EPISODE_VIDEO_URL)
    private String videoUrl;

    @Field(Constants.EPISODE_DURATION)
    private Integer durationInMinutes;
}

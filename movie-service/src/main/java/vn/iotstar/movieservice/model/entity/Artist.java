package vn.iotstar.movieservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.utils.audit.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;

/**
 * Nghệ sĩ tách thành collection riêng chứ không nhúng hẳn vào phim: một người đóng nhiều phim và
 * có tiểu sử riêng cần quản lý ở một chỗ. Phim chỉ nhúng bản sao rút gọn
 * ({@link vn.iotstar.movieservice.model.entity.embedded.CastMember}) để đọc không phải join.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = Constants.ARTIST_COLLECTION)
@TypeAlias("Artist")
public class Artist extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /** Xem ghi chú ở {@link Movie#getVersion()}: thiếu field này thì auditing bị bỏ qua. */
    @Version
    private Long version;

    @Field(Constants.ARTIST_FULL_NAME)
    private String fullName;

    @Field(Constants.ARTIST_BIO)
    private String bio;

    @Field(Constants.ARTIST_AVATAR_URL)
    private String avatarUrl;

    public void normalize() {
        if (fullName != null) fullName = fullName.trim();
        if (bio != null) bio = bio.trim();
        if (avatarUrl != null) avatarUrl = avatarUrl.trim();
    }
}

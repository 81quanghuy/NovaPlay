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

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = Constants.GENRE_COLLECTION)
@TypeAlias("Genre")
public class Genre extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /** Xem ghi chú ở {@link Movie#getVersion()}: thiếu field này thì auditing bị bỏ qua. */
    @Version
    private Long version;

    @Field(Constants.GENRE_NAME)
    private String name;

    @Field(Constants.SLUG)
    private String slug;

    public void normalize() {
        if (name != null) name = name.trim();
        if (slug != null) slug = slug.trim().toLowerCase();
    }
}

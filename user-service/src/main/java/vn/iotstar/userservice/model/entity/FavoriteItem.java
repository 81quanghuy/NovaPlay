package vn.iotstar.userservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@Table(name = FAVORITE_ITEM_TABLE_NAME,
        uniqueConstraints =
            @UniqueConstraint(name = UK_FAV_PROFILE_CONTENT,
                    columnNames = {FAVORITE_ITEM_USER_ID_COLUMN,FAVORITE_ITEM_MOVIE_ID_COLUMN}),
        indexes =
            @Index(name = IDX_FAV_PROFILE, columnList = FAVORITE_ITEM_USER_ID_COLUMN)
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FavoriteItem extends AbstractBaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = FAVORITE_ITEM_ID_COLUMN, columnDefinition = UUID_CONST)
    private UUID favoriteMovieId;

    @Column(name = FAVORITE_ITEM_USER_ID_COLUMN, nullable = false, columnDefinition = UUID_CONST)
    private UUID profileId;

    @Column(name = FAVORITE_ITEM_MOVIE_ID_COLUMN, nullable = false, length = 64)
    private String movieId;

    @Column(name = FAVORITE_ITEM_MOVIE_TYPE_COLUMN, length = 16)
    private String contentType;
}

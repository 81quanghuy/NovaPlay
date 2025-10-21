package vn.iotstar.userservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.utils.audit.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;

import static vn.iotstar.userservice.util.Constants.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = FAVORITE_ITEM_TABLE_NAME)
@TypeAlias("FavoriteItem")
@CompoundIndex(
                name = "uk_profile_movie",
                def = "{'" + FAVORITE_ITEM_USER_ID_COLUMN + "': 1, '" + FAVORITE_ITEM_MOVIE_ID_COLUMN + "': 1}",
                unique = true
)
public class FavoriteItem extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(FAVORITE_ITEM_ID_COLUMN)
    private String favoriteMovieId;

    @Indexed
    @Field(FAVORITE_ITEM_USER_ID_COLUMN)
    private String userId;

    @Field(FAVORITE_ITEM_MOVIE_ID_COLUMN)
    private String movieId;

    @Field(FAVORITE_ITEM_MOVIE_TYPE_COLUMN)
    private String contentType;
}

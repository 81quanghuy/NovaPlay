package vn.iotstar.userservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import vn.iostar.utils.AbstractMappedEntity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = FAVORITE_MOVIES_TABLE_NAME)
public class FavoriteMovies extends AbstractMappedEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = FAVORITE_MOVIES_ID_COLUMN, columnDefinition = "nvarchar(255)")
    private String favoriteMovieId;

    @Column(name = FAVORITE_MOVIES_USER_ID_COLUMN, columnDefinition = "nvarchar(255)")
    private String userId;

    @Column(name = FAVORITE_MOVIES_MOVIE_ID_COLUMN, columnDefinition = "nvarchar(255)")
    private String movieId;

    @Column(name =FAVORITE_MOVIES_ADDED_AT_COLUMN, columnDefinition = "datetime")
    private Date addedAt;

}

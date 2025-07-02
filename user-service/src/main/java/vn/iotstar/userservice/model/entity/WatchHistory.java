package vn.iotstar.userservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = WATCH_HISTORY_TABLE_NAME)
public class WatchHistory extends AbstractBaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = WATCH_HISTORY_ID_COLUMN, nullable = false, unique = true)
    private String watchHistoryId;

    @Column(name = WATCH_HISTORY_USER_ID_COLUMN , nullable = false)
    private String userId;

    @Column(name = WATCH_HISTORY_MOVIE_ID_COLUMN, nullable = false)
    private String movieId;

    @Column(name = WATCH_HISTORY_WATCHED_AT_COLUMN, nullable = false)
    private Long watchedAt; // timestamp in milliseconds

    @Column(name = WATCH_HISTORY_TOTAL_WATCH_TIME_COLUMN, nullable = false)
    private Long totalWatchTime; // total watch time in milliseconds

}

package vn.iotstar.userservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import static vn.iotstar.userservice.util.Constants.*;

@Entity
@Table(name = WATCH_PROGRESS_TABLE_NAME,
        uniqueConstraints = @UniqueConstraint(name = UK_WATCH_PROGRESS_PROFILE_CONTENT,
                columnNames = {WATCH_PROGRESS_USER_ID_COLUMN,WATCH_PROGRESS_MOVIE_ID_COLUMN}),
        indexes = {
                @Index(name = IDX_WATCH_PROGRESS_PROFILE_TIME,
                        columnList = WATCH_PROGRESS_USER_ID_COLUMN + "," + WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN),
                @Index(name = IDX_WATCH_PROGRESS_PROFILE_CONTENT,
                        columnList = WATCH_PROGRESS_USER_ID_COLUMN + ","+ WATCH_PROGRESS_MOVIE_ID_COLUMN)
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WatchProgress extends AbstractBaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = WATCH_PROGRESS_ID_COLUMN)
    private UUID watchHistoryId;

    @Column(name = WATCH_PROGRESS_USER_ID_COLUMN , nullable = false, columnDefinition = UUID_CONST)
    private UUID userId;

    @Column(name = WATCH_PROGRESS_MOVIE_ID_COLUMN, nullable = false, length = 64)
    private String movieId;

    @Column(name = WATCH_PROGRESS_SERIES_ID_COLUMN, length = 64)
    private String seriesId;

    @Column(name = WATCH_PROGRESS_SEASON_ID_COLUMN, length = 64)
    private String seasonId;

    @Column(name = WATCH_PROGRESS_EPISODE_ID_COLUMN, length = 64)
    private String episodeId;

    @Column(name = WATCH_PROGRESS_POSITION_MS_COLUMN, nullable = false)
    private long positionMs;

    @Column(name = WATCH_PROGRESS_DURATION_MS_COLUMN)
    private Long durationMs;

    @Column(name = WATCH_PROGRESS_PERCENT_COLUMN)
    private Integer percent;

    @Column(name = WATCH_PROGRESS_COMPLETED_COLUMN, nullable = false)
    private boolean completed;

    @Column(name = WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN, nullable = false)
    private Instant lastWatchedAt;
}

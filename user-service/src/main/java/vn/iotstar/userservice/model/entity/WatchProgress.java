package vn.iotstar.userservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.userservice.util.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import static vn.iotstar.userservice.util.Constants.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = WATCH_PROGRESS_TABLE_NAME)
@TypeAlias("WatchProgress")
@CompoundIndex(name = "uk_user_movie",
                def = "{'" + WATCH_PROGRESS_USER_ID_COLUMN + "': 1, '" + WATCH_PROGRESS_MOVIE_ID_COLUMN + "': 1}",
                unique = true)
@CompoundIndex(name = "idx_user_lastwatched",
                def = "{'" + WATCH_PROGRESS_USER_ID_COLUMN + "': 1, '" + WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN + "': -1}")
public class WatchProgress extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(WATCH_PROGRESS_ID_COLUMN)
    private String watchHistoryId;

    @Indexed
    @Field(WATCH_PROGRESS_USER_ID_COLUMN)
    private String userId;

    @Field(WATCH_PROGRESS_MOVIE_ID_COLUMN)
    private String movieId;

    @Field(WATCH_PROGRESS_SERIES_ID_COLUMN)
    private String seriesId;

    @Field(WATCH_PROGRESS_SEASON_ID_COLUMN)
    private String seasonId;

    @Field(WATCH_PROGRESS_EPISODE_ID_COLUMN)
    private String episodeId;

    @Field(WATCH_PROGRESS_POSITION_MS_COLUMN)
    private long positionMs;

    @Field(WATCH_PROGRESS_DURATION_MS_COLUMN)
    private Long durationMs;

    @Field(WATCH_PROGRESS_PERCENT_COLUMN)
    private Integer percent;

    @Field(WATCH_PROGRESS_COMPLETED_COLUMN)
    private boolean completed;

    @Field(WATCH_PROGRESS_LAST_WATCHED_AT_COLUMN)
    private Instant lastWatchedAt;
}

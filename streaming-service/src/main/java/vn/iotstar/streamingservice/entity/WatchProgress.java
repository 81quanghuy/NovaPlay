package vn.iotstar.streamingservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import vn.iotstar.streamingservice.common.audit.AuditableDocument;
import vn.iotstar.streamingservice.utils.Constants;

import java.io.Serial;
import java.io.Serializable;

/** Vị trí xem dở của một user cho một phim (và tập, nếu là phim bộ) — nền tảng "continue watching". */
@Document(collection = Constants.WATCH_PROGRESS_COLLECTION)
@CompoundIndex(name = Constants.IDX_WATCH_PROGRESS_UNIQUE,
        def = "{'userEmail': 1, 'movieId': 1, 'episodeNumber': 1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WatchProgress extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private String userEmail;
    private String movieId;

    /** Null với phim lẻ; số tập với phim bộ — phần của khoá unique nên KHÔNG được null lẫn có giá trị hỗn hợp. */
    @Builder.Default
    private Integer episodeNumber = 0;

    private int positionSeconds;
    private int durationSeconds;

    @Version
    private Long version;
}

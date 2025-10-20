package vn.iotstar.movieservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = Constants.EPISODE_TABLE)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Episode extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ID)
    private UUID id;

    @Column(name = Constants.EPISODE_NUMBER, nullable = false)
    private Integer episodeNumber;

    @Column(name = Constants.EPISODE_TITLE, nullable = false)
    private String title;

    @Column(name = Constants.EPISODE_VIDEO_URL, nullable = false)
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.EPISODE_MOVIE_ID, nullable = false)
    private Movie movie;
}
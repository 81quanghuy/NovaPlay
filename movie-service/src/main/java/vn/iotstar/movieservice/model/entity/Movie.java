package vn.iotstar.movieservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Constants.MOVIE_TABLE, indexes = {
        @Index(name = "idx_movie_title", columnList = Constants.MOVIE_TITLE)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Movie extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ID)
    private UUID id;

    @Column(name = Constants.MOVIE_TITLE, nullable = false)
    private String title;

    @Lob
    @Column(name = Constants.MOVIE_DESCRIPTION, nullable = false)
    private String description;

    @Column(name = Constants.MOVIE_RELEASE_DATE)
    private LocalDate releaseDate;

    @Column(name = Constants.MOVIE_DURATION)
    private Integer durationInMinutes;

    @Column(name = Constants.MOVIE_POSTER_URL)
    private String posterUrl;

    @Column(name = Constants.MOVIE_IS_SERIES, nullable = false)
    private boolean isSeries = false;

    // --- Relationships ---

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = Constants.MOVIE_GENRE_TABLE,
            joinColumns = @JoinColumn(name = Constants.MA_MOVIE_ID),
            inverseJoinColumns = @JoinColumn(name = Constants.MA_ARTIST_ID)
    )
    @Builder.Default
    private Set<Genre> genres = new HashSet<>();

    @OneToMany(
            mappedBy = "movie",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private Set<MovieArtist> artists = new HashSet<>();

    @OneToMany(
            mappedBy = "movie",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("episodeNumber ASC") // Always keep episodes in order
    @Builder.Default
    private List<Episode> episodes = new ArrayList<>();
}
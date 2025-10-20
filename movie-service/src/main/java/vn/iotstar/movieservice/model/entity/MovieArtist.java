package vn.iotstar.movieservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = Constants.MOVIE_ARTIST_TABLE)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MovieArtist extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.MA_MOVIE_ID)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.MA_ARTIST_ID)
    private Artist artist;

    @Column(name = Constants.MA_ROLE, nullable = false)
    private String role;

    @Column(name = Constants.MA_CHARACTER_NAME)
    private String characterName;
}
package vn.iotstar.movieservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.movieservice.utils.Constants;
import vn.iotstar.utils.AbstractBaseEntity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Constants.ARTIST_TABLE)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Artist extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ID)
    private UUID id;

    @Column(name = Constants.ARTIST_FULL_NAME, nullable = false)
    private String fullName;

    @Lob
    @Column(name = Constants.ARTIST_BIO)
    private String bio;

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<MovieArtist> movies = new HashSet<>();
}
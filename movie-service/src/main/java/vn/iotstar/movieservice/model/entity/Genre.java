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
@Table(name = Constants.GENRE_TABLE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Genre extends AbstractBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = Constants.ID)
    private UUID id;

    @Column(name = Constants.GENRE_NAME, nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "genres")
    @Builder.Default
    private Set<Movie> movies = new HashSet<>();
}
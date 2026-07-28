package vn.iotstar.movieservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.movieservice.model.entity.Genre;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends MongoRepository<Genre, String> {

    Optional<Genre> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    List<Genre> findAllByIdIn(Collection<String> ids);

    List<Genre> findAllByOrderByNameAsc();
}

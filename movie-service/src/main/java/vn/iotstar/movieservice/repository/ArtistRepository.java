package vn.iotstar.movieservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.movieservice.model.entity.Artist;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArtistRepository extends MongoRepository<Artist, String> {

    Page<Artist> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    List<Artist> findAllByIdIn(Collection<String> ids);
}

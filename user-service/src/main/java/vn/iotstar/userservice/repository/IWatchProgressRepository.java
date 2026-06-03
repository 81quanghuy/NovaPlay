package vn.iotstar.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.WatchProgress;

import java.util.Optional;

@Repository
public interface IWatchProgressRepository extends MongoRepository<WatchProgress, String> {

    Optional<WatchProgress> findByUserIdAndMovieId(String userId, String movieId);

    Page<WatchProgress> findByUserIdOrderByLastWatchedAtDesc(String userId, Pageable pageable);
}

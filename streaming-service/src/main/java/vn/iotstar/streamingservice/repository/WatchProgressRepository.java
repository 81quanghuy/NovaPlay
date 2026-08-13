package vn.iotstar.streamingservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.streamingservice.entity.WatchProgress;

import java.util.Optional;

@Repository
public interface WatchProgressRepository extends MongoRepository<WatchProgress, String> {

    Optional<WatchProgress> findByUserEmailAndMovieIdAndEpisodeNumber(String userEmail, String movieId, Integer episodeNumber);

    /** "Continue watching" — mới xem gần đây nhất trước. */
    Page<WatchProgress> findByUserEmailOrderByUpdatedAtDesc(String userEmail, Pageable pageable);
}

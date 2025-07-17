package vn.iotstar.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.WatchHistory;

import java.util.Collection;
import java.util.List;

@Repository
public interface IWatchHistoryRepository extends JpaRepository<WatchHistory, String> {

    WatchHistory findByUserIdAndMovieId(String userId, String videoId);
    List<WatchHistory> findAllByUserId(String userId);
    Page<WatchHistory> findByUserId(String userId, Pageable pageable);
    Boolean existsByUserIdAndMovieId(String userId, String videoId);
    WatchHistory findByUserId(String userId);
    void deleteByUserIdAndMovieId(String userId, String videoId);
    void deleteByUserId(String userId);
    void deleteByMovieId(String videoId);
    void deleteByUserIdAndMovieIdIn(String userId, Collection<String> movieId);

}

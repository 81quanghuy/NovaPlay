package vn.iotstar.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.FavoriteMovies;

import java.util.List;
import java.util.Optional;

@Repository
public interface IFavoriteMoviesRepository extends JpaRepository<FavoriteMovies, String> {
    // Define custom query methods if needed
    Optional<FavoriteMovies> findByUserIdAndMovieId(String userId, String movieId);
    Boolean existsByUserIdAndMovieId(String userId, String movieId);
    List<FavoriteMovies> findByUserId(String userId);
    Boolean existsByUserId(String userId);
    void deleteAllByUserId(String userId);
}

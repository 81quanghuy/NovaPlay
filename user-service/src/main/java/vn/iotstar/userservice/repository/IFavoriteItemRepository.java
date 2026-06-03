package vn.iotstar.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.FavoriteItem;

import java.util.Optional;

@Repository
public interface IFavoriteItemRepository extends MongoRepository<FavoriteItem, String> {

    Page<FavoriteItem> findByUserId(String userId, Pageable pageable);

    Optional<FavoriteItem> findByUserIdAndMovieId(String userId, String movieId);

    boolean existsByUserIdAndMovieId(String userId, String movieId);

    void deleteByUserIdAndMovieId(String userId, String movieId);

    void deleteAllByUserId(String userId);
}

package vn.iotstar.userservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.userservice.model.dto.AddFavoriteItemRequest;
import vn.iotstar.userservice.model.entity.FavoriteItem;

public interface FavoriteMoviesService {

    FavoriteItem addFavoriteMovie(String email, AddFavoriteItemRequest request);

    void removeFavoriteMovie(String email, String movieId);

    Page<FavoriteItem> getFavoriteMovies(String email, Pageable pageable);

    boolean isFavoriteMovie(String email, String movieId);

    void deleteAllFavoriteMovies(String email);
}

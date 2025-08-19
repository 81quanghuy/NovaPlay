package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.repository.IFavoriteItemRepository;
import vn.iotstar.userservice.service.IFavoriteMoviesService;
import vn.iotstar.utils.constants.GenericResponse;

import static vn.iotstar.userservice.util.MessageProperties.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteMoviesService implements IFavoriteMoviesService {

    private final IFavoriteItemRepository favoriteMoviesRepository;

    @Override
    public ResponseEntity<GenericResponse> addFavoriteMovie(String userId, String movieId) {
        return null;
    }

    @Override
    public ResponseEntity<GenericResponse> removeFavoriteMovie(String userId, String movieId) {
        return null;
    }

    @Override
    public ResponseEntity<GenericResponse> getFavoriteMovies(String userId) {
        return null;
    }

    @Override
    public ResponseEntity<GenericResponse> isFavoriteMovie(String userId, String movieId) {
        return null;
    }

    @Override
    public ResponseEntity<GenericResponse> deleteAllFavoriteMovies(String userId) {
        return null;
    }
}

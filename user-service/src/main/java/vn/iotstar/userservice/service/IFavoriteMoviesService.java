package vn.iotstar.userservice.service;

import org.springframework.http.ResponseEntity;
import vn.iotstar.utils.constants.GenericResponse;

public interface IFavoriteMoviesService {
    ResponseEntity<GenericResponse> addFavoriteMovie(String userId, String movieId);

    ResponseEntity<GenericResponse> removeFavoriteMovie(String userId, String movieId);

    ResponseEntity<GenericResponse> getFavoriteMovies(String userId);

    ResponseEntity<GenericResponse> isFavoriteMovie(String userId, String movieId);

    ResponseEntity<GenericResponse> deleteAllFavoriteMovies(String userId);
}

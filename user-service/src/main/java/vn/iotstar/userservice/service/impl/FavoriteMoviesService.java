package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.entity.FavoriteMovies;
import vn.iotstar.userservice.repository.IFavoriteMoviesRepository;
import vn.iotstar.userservice.service.IFavoriteMoviesService;

import java.util.Date;

import static vn.iotstar.userservice.util.MessageProperties.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteMoviesService implements IFavoriteMoviesService {

    private final IFavoriteMoviesRepository favoriteMoviesRepository;


    /**
     * Adds a movie to the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to be added to favorites.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @Override
    public ResponseEntity<GenericResponse> addFavoriteMovie(String userId, String movieId) {
        log.info("Adding movie with ID {} to favorites for user {}", movieId, userId);
        if (favoriteMoviesRepository.existsByUserIdAndMovieId(userId, movieId)) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(FAVORITE_MOVIE_ALREADY_EXISTS)
                    .statusCode(400)
                    .build());
        }
        FavoriteMovies favoriteMovie = FavoriteMovies.builder()
                .userId(userId)
                .movieId(movieId)
                .addedAt(new Date())
                .build();
        favoriteMoviesRepository.save(favoriteMovie);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(FAVORITE_MOVIE_CREATE_SUCCESS)
                .statusCode(200)
                .build());
    }

    /**
     * Removes a movie from the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to be removed from favorites.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @Override
    public ResponseEntity<GenericResponse> removeFavoriteMovie(String userId, String movieId) {
        log.info("Removing movie with ID {} from favorites for user {}", movieId, userId);
        FavoriteMovies favoriteMovie = favoriteMoviesRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new RuntimeException(FAVORITE_MOVIE_NOT_FOUND));
        favoriteMoviesRepository.delete(favoriteMovie);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(FAVORITE_MOVIE_DELETE_SUCCESS)
                .statusCode(200)
                .build());
    }

    /**
     * Retrieves all favorite movies for a user.
     *
     * @param userId The ID of the user whose favorite movies are to be fetched.
     * @return ResponseEntity with GenericResponse containing the list of favorite movies or an error message.
     */
    @Override
    public ResponseEntity<GenericResponse> getFavoriteMovies(String userId) {
        log.info("Fetching favorite movies for user {}", userId);
        var favoriteMovies = favoriteMoviesRepository.findByUserId(userId);
        if (favoriteMovies.isEmpty()) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(FAVORITE_MOVIE_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(FAVORITE_MOVIE_RETRIEVE_ALL_SUCCESS)
                .result(favoriteMovies)
                .statusCode(200)
                .build());
    }

    /**
     * Checks if a movie is in the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to be checked.
     * @return ResponseEntity with GenericResponse indicating whether the movie is a favorite or not.
     */
    @Override
    public ResponseEntity<GenericResponse> isFavoriteMovie(String userId, String movieId) {
        log.info("Checking if movie with ID {} is a favorite for user {}", movieId, userId);
        boolean isFavorite = favoriteMoviesRepository.existsByUserIdAndMovieId(userId, movieId);
        if (isFavorite) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(true)
                    .message(FAVORITE_MOVIE_ALREADY_EXISTS)
                    .statusCode(200)
                    .build());
        } else {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(FAVORITE_MOVIE_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }
    }

    /**
     * Deletes all favorite movies for a user.
     *
     * @param userId The ID of the user whose favorite movies are to be deleted.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @Override
    public ResponseEntity<GenericResponse> deleteAllFavoriteMovies(String userId) {
        log.info("Deleting all favorite movies for user {}", userId);
        if (!favoriteMoviesRepository.existsByUserId(userId)) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(FAVORITE_MOVIE_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }
        favoriteMoviesRepository.deleteAllByUserId(userId);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(FAVORITE_MOVIE_DELETE_ALL_SUCCESS)
                .statusCode(200)
                .build());
    }

}

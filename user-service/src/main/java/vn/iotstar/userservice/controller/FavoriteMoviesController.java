package vn.iotstar.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.userservice.service.IFavoriteMoviesService;

@RestController
@Slf4j
@RequiredArgsConstructor
public class FavoriteMoviesController {
    private final IFavoriteMoviesService favoriteMoviesService;

    /**
     * Adds a movie to the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to be added.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @PostMapping("/add")
    public ResponseEntity<GenericResponse> addFavoriteMovie(String userId, String movieId) {
        log.info("Adding favorite movie for user: {}, movie: {}", userId, movieId);
        return favoriteMoviesService.addFavoriteMovie(userId, movieId);
    }

    /**
     * Removes a movie from the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to be removed.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @DeleteMapping("/remove")
    public ResponseEntity<GenericResponse> removeFavoriteMovie(String userId, String movieId) {
        log.info("Removing favorite movie for user: {}, movie: {}", userId, movieId);
        return favoriteMoviesService.removeFavoriteMovie(userId, movieId);
    }

    /**
     * Retrieves the list of favorite movies for a user.
     *
     * @param userId The ID of the user.
     * @return ResponseEntity with GenericResponse containing the list of favorite movies.
     */
    @GetMapping("/list")
    public ResponseEntity<GenericResponse> getFavoriteMovies(String userId) {
        log.info("Fetching favorite movies for user: {}", userId);
        return favoriteMoviesService.getFavoriteMovies(userId);
    }

    /**
     * Checks if a movie is in the user's favorite list.
     *
     * @param userId  The ID of the user.
     * @param movieId The ID of the movie to check.
     * @return ResponseEntity with GenericResponse indicating whether the movie is a favorite.
     */
    @GetMapping("/is-favorite")
    public ResponseEntity<GenericResponse> isFavoriteMovie(String userId, String movieId) {
        log.info("Checking if movie is favorite for user: {}, movie: {}", userId, movieId);
        return favoriteMoviesService.isFavoriteMovie(userId, movieId);
    }

    /**
     * Deletes all favorite movies for a user.
     *
     * @param userId The ID of the user.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @DeleteMapping("/delete-all")
    public ResponseEntity<GenericResponse> deleteAllFavoriteMovies(String userId) {
        log.info("Deleting all favorite movies for user: {}", userId);
        return favoriteMoviesService.deleteAllFavoriteMovies(userId);
    }
}

package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.model.dto.AddFavoriteItemRequest;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IFavoriteItemRepository;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.service.FavoriteMoviesService;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteMoviesServiceImpl implements FavoriteMoviesService {

    private final IFavoriteItemRepository favoriteItemRepository;
    private final IUserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public FavoriteItem addFavoriteMovie(String email, AddFavoriteItemRequest request) {
        String userId = resolveUserId(email);
        if (favoriteItemRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Movie already in favorites");
        }
        FavoriteItem item = FavoriteItem.builder()
                .favoriteMovieId(UUID.randomUUID().toString())
                .userId(userId)
                .movieId(request.movieId())
                .contentType(request.movieType())
                .build();
        try {
            return favoriteItemRepository.save(item);
        } catch (DuplicateKeyException e) {
            // race condition — treat as already exists
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Movie already in favorites");
        }
    }

    @Override
    @Transactional
    public void removeFavoriteMovie(String email, String movieId) {
        String userId = resolveUserId(email);
        // idempotent — no error if not found
        favoriteItemRepository.deleteByUserIdAndMovieId(userId, movieId);
    }

    @Override
    public Page<FavoriteItem> getFavoriteMovies(String email, Pageable pageable) {
        String userId = resolveUserId(email);
        return favoriteItemRepository.findByUserId(userId, pageable);
    }

    @Override
    public boolean isFavoriteMovie(String email, String movieId) {
        String userId = resolveUserId(email);
        return favoriteItemRepository.existsByUserIdAndMovieId(userId, movieId);
    }

    @Override
    @Transactional
    public void deleteAllFavoriteMovies(String email) {
        String userId = resolveUserId(email);
        favoriteItemRepository.deleteAllByUserId(userId);
    }

    private String resolveUserId(String email) {
        return userProfileRepository.findByEmail(email)
                .map(UserProfile::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    }
}

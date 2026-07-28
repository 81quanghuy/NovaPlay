package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.model.dto.AddFavoriteItemRequest;
import vn.iotstar.userservice.model.entity.FavoriteItem;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.repository.IFavoriteItemRepository;
import vn.iotstar.userservice.service.AuditLogger;
import vn.iotstar.userservice.service.FavoriteMoviesService;
import vn.iotstar.userservice.service.UserProfileLookup;
import vn.iotstar.utils.exceptions.wrapper.UserAlreadyExistsException;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteMoviesServiceImpl implements FavoriteMoviesService {

    private final IFavoriteItemRepository favoriteItemRepository;
    private final UserProfileLookup userProfileLookup;
    private final UserMetrics metrics;
    private final AuditLogger auditLogger;

    @Override
    public FavoriteItem addFavoriteMovie(String email, AddFavoriteItemRequest request) {
        String userId = userProfileLookup.resolveUserId(email);
        if (favoriteItemRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            throw new UserAlreadyExistsException("Movie already in favorites");
        }
        FavoriteItem item = FavoriteItem.builder()
                .favoriteMovieId(UUID.randomUUID().toString())
                .userId(userId)
                .movieId(request.movieId())
                .contentType(request.movieType())
                .build();
        try {
            FavoriteItem saved = favoriteItemRepository.save(item);
            metrics.getFavoriteAdded().increment();
            auditLogger.favoriteAdded(email, request.movieId());
            return saved;
        } catch (DuplicateKeyException e) {
            // Hai request song song cùng thêm một phim: unique index uk_profile_movie đã chặn.
            throw new UserAlreadyExistsException("Movie already in favorites");
        }
    }

    @Override
    public void removeFavoriteMovie(String email, String movieId) {
        String userId = userProfileLookup.resolveUserId(email);
        // idempotent — không lỗi nếu bản ghi không tồn tại
        favoriteItemRepository.deleteByUserIdAndMovieId(userId, movieId);
        metrics.getFavoriteRemoved().increment();
    }

    @Override
    public Page<FavoriteItem> getFavoriteMovies(String email, Pageable pageable) {
        String userId = userProfileLookup.resolveUserId(email);
        return favoriteItemRepository.findByUserId(userId, pageable);
    }

    @Override
    public boolean isFavoriteMovie(String email, String movieId) {
        String userId = userProfileLookup.resolveUserId(email);
        return favoriteItemRepository.existsByUserIdAndMovieId(userId, movieId);
    }

    @Override
    public void deleteAllFavoriteMovies(String email) {
        String userId = userProfileLookup.resolveUserId(email);
        long removed = favoriteItemRepository.deleteAllByUserId(userId);
        auditLogger.favoritesCleared(email, removed);
    }
}

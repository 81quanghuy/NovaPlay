package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.model.dto.UpsertWatchProgressRequest;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.model.entity.WatchProgress;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.repository.IWatchProgressRepository;
import vn.iotstar.userservice.service.WatchHistoryService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private final IWatchProgressRepository watchProgressRepository;
    private final IUserProfileRepository userProfileRepository;

    private static final int COMPLETED_THRESHOLD_PERCENT = 95;

    @Override
    @Transactional
    public WatchProgress upsertWatchProgress(String email, UpsertWatchProgressRequest request) {
        String userId = resolveUserId(email);

        WatchProgress progress = watchProgressRepository
                .findByUserIdAndMovieId(userId, request.movieId())
                .orElseGet(() -> WatchProgress.builder()
                        .watchHistoryId(UUID.randomUUID().toString())
                        .userId(userId)
                        .movieId(request.movieId())
                        .build());

        progress.setPositionMs(request.positionMs());
        if (request.durationMs() != null) {
            progress.setDurationMs(request.durationMs());
        }
        progress.setSeriesId(request.seriesId());
        progress.setSeasonId(request.seasonId());
        progress.setEpisodeId(request.episodeId());
        progress.setLastWatchedAt(Instant.now());

        Integer percent = null;
        if (progress.getDurationMs() != null && progress.getDurationMs() > 0) {
            percent = (int) (request.positionMs() * 100L / progress.getDurationMs());
        }
        progress.setPercent(percent);

        boolean completed = Boolean.TRUE.equals(request.ended())
                || (percent != null && percent >= COMPLETED_THRESHOLD_PERCENT);
        progress.setCompleted(completed);

        return watchProgressRepository.save(progress);
    }

    @Override
    public Page<WatchProgress> getRecentWatchProgress(String email, Pageable pageable) {
        String userId = resolveUserId(email);
        return watchProgressRepository.findByUserIdOrderByLastWatchedAtDesc(userId, pageable);
    }

    @Override
    public Optional<WatchProgress> getWatchProgress(String email, String movieId) {
        String userId = resolveUserId(email);
        return watchProgressRepository.findByUserIdAndMovieId(userId, movieId);
    }

    private String resolveUserId(String email) {
        return userProfileRepository.findByEmail(email)
                .map(UserProfile::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    }
}

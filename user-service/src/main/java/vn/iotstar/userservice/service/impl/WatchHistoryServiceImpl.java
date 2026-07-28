package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.model.dto.UpsertWatchProgressRequest;
import vn.iotstar.userservice.model.entity.WatchProgress;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.repository.IWatchProgressRepository;
import vn.iotstar.userservice.service.UserProfileLookup;
import vn.iotstar.userservice.service.WatchHistoryService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private final IWatchProgressRepository watchProgressRepository;
    private final UserProfileLookup userProfileLookup;
    private final UserMetrics metrics;

    private static final int COMPLETED_THRESHOLD_PERCENT = 95;

    @Override
    public WatchProgress upsertWatchProgress(String email, UpsertWatchProgressRequest request) {
        String userId = userProfileLookup.resolveUserId(email);

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

        Integer percent = computePercent(request.positionMs(), progress.getDurationMs());
        progress.setPercent(percent);

        boolean completed = Boolean.TRUE.equals(request.ended())
                || (percent != null && percent >= COMPLETED_THRESHOLD_PERCENT);
        progress.setCompleted(completed);

        WatchProgress saved = watchProgressRepository.save(progress);
        metrics.getWatchProgressUpserted().increment();
        return saved;
    }

    /**
     * Trả về phần trăm đã xem, hoặc null nếu chưa biết thời lượng.
     * <p>
     * Kết quả luôn được kẹp trong [0, 100]: {@code durationMs} là snapshot do client gửi lên
     * và có thể lệch so với vị trí phát thực tế, khiến phép chia thô vượt quá 100.
     */
    private static Integer computePercent(long positionMs, Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return null;
        }
        long raw = positionMs * 100L / durationMs;
        return (int) Math.max(0L, Math.min(100L, raw));
    }

    @Override
    public Page<WatchProgress> getRecentWatchProgress(String email, Pageable pageable) {
        String userId = userProfileLookup.resolveUserId(email);
        return watchProgressRepository.findByUserIdOrderByLastWatchedAtDesc(userId, pageable);
    }

    @Override
    public Optional<WatchProgress> getWatchProgress(String email, String movieId) {
        String userId = userProfileLookup.resolveUserId(email);
        return watchProgressRepository.findByUserIdAndMovieId(userId, movieId);
    }
}

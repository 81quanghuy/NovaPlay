package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.iotstar.streamingservice.entity.WatchProgress;
import vn.iotstar.streamingservice.exception.BadRequestException;
import vn.iotstar.streamingservice.exception.ForbiddenException;
import vn.iotstar.streamingservice.exception.ResourceNotFoundException;
import vn.iotstar.streamingservice.model.dto.ContinueWatchingItemDto;
import vn.iotstar.streamingservice.model.dto.ManifestResponseDto;
import vn.iotstar.streamingservice.model.dto.PageResponse;
import vn.iotstar.streamingservice.model.dto.WatchProgressRequest;
import vn.iotstar.streamingservice.repository.WatchProgressRepository;
import vn.iotstar.streamingservice.service.PlaybackTokenService;
import vn.iotstar.streamingservice.service.StreamingService;
import vn.iotstar.streamingservice.service.ViewCountService;
import vn.iotstar.streamingservice.service.client.dto.EpisodeResponse;
import vn.iotstar.streamingservice.service.client.dto.MovieDetailResponse;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;
import vn.iotstar.streamingservice.util.Plan;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StreamingServiceImpl implements StreamingService {

    private static final String READY = "READY";
    private static final int STANDALONE_EPISODE_SLOT = 0;

    private final CachedMovieResolver movieResolver;
    private final CachedManifestResolver manifestResolver;
    private final CachedEntitlementResolver entitlementResolver;
    private final PlaybackTokenService playbackTokenService;
    private final WatchProgressRepository watchProgressRepository;
    private final ViewCountService viewCountService;

    @Override
    public ManifestResponseDto resolvePlayback(String idOrSlug, Integer episodeNumber, String userEmail) {
        MovieDetailResponse movie = fetchMovie(idOrSlug);

        // Cổng entitlement TRƯỚC khi chạm vào mediaId/manifest — fail-closed: bất kỳ lỗi nào khi
        // hỏi user-service (kể cả circuit mở) đều văng exception ra ngoài thay vì cho qua, xem
        // CachedEntitlementResolver.
        Plan required = Plan.fromName(movie.minPlan());
        Plan userPlan = entitlementResolver.resolvePlan(userEmail);
        if (!userPlan.atLeast(required)) {
            throw new ForbiddenException(
                    "This title requires the " + required + " plan (you are on " + userPlan + ").");
        }

        String mediaId = resolveMediaId(movie, episodeNumber);
        if (mediaId == null || mediaId.isBlank()) {
            throw new ResourceNotFoundException("This title has no video attached yet.");
        }

        VideoManifestResponse manifest = manifestResolver.resolveByMediaId(mediaId);
        if (!READY.equals(manifest.status())) {
            throw new ResourceNotFoundException("Video is still processing (status=" + manifest.status() + ").");
        }

        String token = playbackTokenService.mint(userEmail, manifest.id());
        String masterUrl = "/api/v1/streaming/hls/" + manifest.id() + "/master.m3u8?pt=" + token;

        int resumeSlot = normalizeEpisodeSlot(episodeNumber);
        int resumePosition = watchProgressRepository
                .findByUserEmailAndMovieIdAndEpisodeNumber(userEmail, movie.id(), resumeSlot)
                .map(WatchProgress::getPositionSeconds)
                .orElse(0);

        return new ManifestResponseDto(movie.title(), manifest.id(), masterUrl, manifest.durationSeconds(), resumePosition);
    }

    @Override
    public VideoManifestResponse getManifestById(String manifestId) {
        return manifestResolver.resolveByMediaId(manifestId);
    }

    @Override
    public void upsertProgress(String userEmail, String idOrSlug, WatchProgressRequest request) {
        MovieDetailResponse movie = fetchMovie(idOrSlug);
        int slot = normalizeEpisodeSlot(request.episodeNumber());
        if (movie.series() && slot == STANDALONE_EPISODE_SLOT) {
            throw new BadRequestException("episodeNumber is required for a series.");
        }

        WatchProgress progress = watchProgressRepository
                .findByUserEmailAndMovieIdAndEpisodeNumber(userEmail, movie.id(), slot)
                .orElseGet(() -> WatchProgress.builder()
                        .userEmail(userEmail)
                        .movieId(movie.id())
                        .episodeNumber(slot)
                        .build());

        progress.setPositionSeconds(request.positionSeconds());
        if (request.durationSeconds() != null) {
            progress.setDurationSeconds(request.durationSeconds());
        }
        watchProgressRepository.save(progress);
    }

    @Override
    public PageResponse<ContinueWatchingItemDto> continueWatching(String userEmail, Pageable pageable) {
        Page<WatchProgress> page = watchProgressRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail, pageable);
        return PageResponse.from(page, p -> new ContinueWatchingItemDto(
                p.getMovieId(),
                p.getEpisodeNumber() == STANDALONE_EPISODE_SLOT ? null : p.getEpisodeNumber(),
                p.getPositionSeconds(), p.getDurationSeconds(), p.getUpdatedAt()));
    }

    @Override
    public void recordView(String userEmail, String idOrSlug) {
        MovieDetailResponse movie = fetchMovie(idOrSlug);
        viewCountService.recordView(userEmail, movie.id());
    }

    private MovieDetailResponse fetchMovie(String idOrSlug) {
        return movieResolver.resolveByIdOrSlug(idOrSlug);
    }

    private String resolveMediaId(MovieDetailResponse movie, Integer episodeNumber) {
        if (!movie.series()) {
            return movie.mediaId();
        }
        List<EpisodeResponse> episodes = movie.episodes() == null ? List.of() : movie.episodes();
        Optional<EpisodeResponse> episode = episodes.stream()
                .filter(e -> e.episodeNumber() != null && e.episodeNumber().equals(episodeNumber))
                .findFirst();
        return episode.map(EpisodeResponse::mediaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Episode " + episodeNumber + " not found for series " + movie.id()));
    }

    /** {@code null}/không truyền = phim lẻ, dùng slot cố định 0 để khớp unique index của WatchProgress. */
    private int normalizeEpisodeSlot(Integer episodeNumber) {
        return episodeNumber == null ? STANDALONE_EPISODE_SLOT : episodeNumber;
    }
}

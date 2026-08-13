package vn.iotstar.streamingservice.service;

import org.springframework.data.domain.Pageable;
import vn.iotstar.streamingservice.model.dto.ContinueWatchingItemDto;
import vn.iotstar.streamingservice.model.dto.ManifestResponseDto;
import vn.iotstar.streamingservice.model.dto.PageResponse;
import vn.iotstar.streamingservice.model.dto.WatchProgressRequest;
import vn.iotstar.streamingservice.service.client.dto.VideoManifestResponse;

public interface StreamingService {

    /**
     * Phân giải phim/tập -> mediaId -> manifest HLS, mint playback token, trả về URL master
     * playlist đã gắn token sẵn.
     *
     * @throws vn.iotstar.streamingservice.exception.ResourceNotFoundException phim/tập không có
     *         video gắn kèm, hoặc manifest chưa {@code READY} (còn đang transcode/lỗi)
     */
    ManifestResponseDto resolvePlayback(String idOrSlug, Integer episodeNumber, String userEmail);

    /** Manifest đầy đủ cho một id — dùng bởi {@code StreamingHlsController} sau khi token đã hợp lệ. */
    VideoManifestResponse getManifestById(String manifestId);

    void upsertProgress(String userEmail, String idOrSlug, WatchProgressRequest request);

    PageResponse<ContinueWatchingItemDto> continueWatching(String userEmail, Pageable pageable);

    void recordView(String userEmail, String idOrSlug);
}

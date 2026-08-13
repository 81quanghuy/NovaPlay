package vn.iotstar.mediaservice.service;

import vn.iotstar.mediaservice.common.dto.VideoManifestCompleteRequest;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.entity.VideoManifest;

import java.time.Instant;
import java.util.List;

public interface VideoManifestService {

    /**
     * Tạo manifest {@code QUEUED} cho một {@link Media} video vừa upload xong, và publish
     * {@code VideoSourceReadyEvent} lên {@code video-source-ready.v1} để transcoding-worker nhặt.
     */
    VideoManifest createQueuedFor(Media sourceMedia);

    VideoManifest getById(String id);

    /** @throws vn.iotstar.mediaservice.exception.ResourceNotFoundException nếu chưa có manifest */
    VideoManifest getBySourceMediaId(String sourceMediaId);

    VideoManifest markProcessing(String id);

    VideoManifest markComplete(String id, VideoManifestCompleteRequest request);

    VideoManifest markFailed(String id, String reason);

    /** [ADMIN] Đẩy lại event cho một manifest đang FAILED, tăng attemptCount. */
    VideoManifest retry(String id);

    /** Manifest kẹt ở PROCESSING quá {@code threshold} — job đối soát dùng để phát hiện worker chết giữa chừng. */
    List<VideoManifest> findStuckProcessing(Instant threshold);

    /** Worker đã chết giữa chừng: retry nếu còn lượt, ngược lại đánh FAILED hẳn. */
    void reconcileStuck(VideoManifest manifest, int maxAttempts);
}

package vn.iotstar.mediaservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.entity.VideoManifest;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.mediaservice.service.VideoManifestService;
import vn.iotstar.mediaservice.util.MediaStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PendingMediaCleanupJob {

    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final VideoManifestService videoManifestService;

    // Lấy giá trị từ application.yml, giúp code linh hoạt hơn
    @Value("${job.cleanup.pending-older-than-hours:1}")
    private long pendingOlderThanHours;

    @Value("${job.cleanup.fail-older-than-hours:24}")
    private long failOlderThanHours;

    @Value("${job.cleanup.transcode-stuck-after-minutes:120}")
    private long transcodeStuckAfterMinutes;

    @Value("${job.cleanup.transcode-max-attempts:3}")
    private int transcodeMaxAttempts;

    @Scheduled(fixedRateString = "${job.cleanup.fixed-rate-ms:3600000}")
    public void findAndFixOrphanedUploads() {
        log.info("Running job to find and fix orphaned uploads...");

        Instant oneHourAgo = Instant.now().minus(pendingOlderThanHours, ChronoUnit.HOURS);
        List<Media> pendingMedias = mediaRepository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING,oneHourAgo);
        if (pendingMedias.isEmpty()) {
            log.info("No pending media records found to process.");
            return;
        }

        log.info("Found {} pending media records to check.", pendingMedias.size());

        for (Media media : pendingMedias) {
            try {
                boolean fileExists = mediaService.doesS3ObjectExist(media);

                if (fileExists) {
                    log.warn("Found orphaned file on S3 for pending record {}. Manually triggering processing.", media.getId());
                    mediaService.processSuccessfulUpload(media);
                } else {
                    Instant failThreshold = Instant.now().minus(failOlderThanHours, ChronoUnit.HOURS);
                    if (media.getCreatedAt().isBefore(failThreshold)) {
                        log.warn("Pending record {} is older than {} hours and file does not exist on S3. Marking as FAILED.", media.getId(), failOlderThanHours);
                        mediaService.markAsFailed(media);
                    }
                }
            } catch (Exception e) {
                log.error("Error during cleanup job for mediaId {}", media.getId(), e);
            }
        }
        log.info("Cleanup job finished.");
    }

    /**
     * Đối soát manifest kẹt ở {@code PROCESSING} quá lâu — dấu hiệu pod worker chết giữa chừng
     * (Kafka đã ack message nên sẽ không tự động redeliver). Re-queue nếu còn lượt thử, ngược lại
     * đánh {@code FAILED} hẳn để không kẹt vĩnh viễn.
     */
    @Scheduled(fixedRateString = "${job.cleanup.fixed-rate-ms:3600000}")
    public void findAndFixStuckTranscodes() {
        Instant threshold = Instant.now().minus(transcodeStuckAfterMinutes, ChronoUnit.MINUTES);
        List<VideoManifest> stuck = videoManifestService.findStuckProcessing(threshold);
        if (stuck.isEmpty()) {
            return;
        }

        log.warn("Found {} video manifest(s) stuck in PROCESSING for over {} minutes.", stuck.size(), transcodeStuckAfterMinutes);
        for (VideoManifest manifest : stuck) {
            try {
                videoManifestService.reconcileStuck(manifest, transcodeMaxAttempts);
            } catch (Exception e) {
                log.error("Error reconciling stuck manifest {}", manifest.getId(), e);
            }
        }
    }
}
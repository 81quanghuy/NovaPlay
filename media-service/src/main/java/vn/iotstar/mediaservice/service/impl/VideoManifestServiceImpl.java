package vn.iotstar.mediaservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.mediaservice.common.dto.RenditionRequest;
import vn.iotstar.mediaservice.common.dto.VideoManifestCompleteRequest;
import vn.iotstar.mediaservice.common.dto.VideoSourceReadyEvent;
import vn.iotstar.mediaservice.common.dto.VideoTranscodeCompletedEvent;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.entity.VideoManifest;
import vn.iotstar.mediaservice.exception.BadRequestException;
import vn.iotstar.mediaservice.exception.ResourceNotFoundException;
import vn.iotstar.mediaservice.repository.VideoManifestRepository;
import vn.iotstar.mediaservice.service.VideoManifestService;
import vn.iotstar.mediaservice.util.TopicNames;
import vn.iotstar.mediaservice.util.VideoManifestStatus;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoManifestServiceImpl implements VideoManifestService {

    private final VideoManifestRepository videoManifestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public VideoManifest createQueuedFor(Media sourceMedia) {
        VideoManifest manifest = videoManifestRepository.save(VideoManifest.builder()
                .id(sourceMedia.getId())
                .sourceMediaId(sourceMedia.getId())
                .ownerId(sourceMedia.getOwnerId())
                .ownerEmail(sourceMedia.getOwnerEmail())
                .status(VideoManifestStatus.QUEUED)
                .storageProvider(sourceMedia.getEffectiveStorageProvider())
                .build());

        publishSourceReady(manifest, sourceMedia);
        return manifest;
    }

    private void publishSourceReady(VideoManifest manifest, Media sourceMedia) {
        VideoSourceReadyEvent event = new VideoSourceReadyEvent(
                manifest.getId(), sourceMedia.getId(), sourceMedia.getOwnerId(), sourceMedia.getOwnerEmail(),
                sourceMedia.getS3Key(), manifest.getStorageProvider().name(),
                sourceMedia.getContentType(), sourceMedia.getSize());
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(TopicNames.VIDEO_SOURCE_READY, event);
            log.info("Sent VideoSourceReadyEvent for manifestId: {}", manifest.getId());
            return true;
        });
    }

    @Override
    public VideoManifest getById(String id) {
        return videoManifestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video manifest not found: " + id));
    }

    @Override
    public VideoManifest getBySourceMediaId(String sourceMediaId) {
        return videoManifestRepository.findBySourceMediaId(sourceMediaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No video manifest for source media: " + sourceMediaId));
    }

    @Override
    public VideoManifest markProcessing(String id) {
        VideoManifest manifest = getById(id);
        manifest.setStatus(VideoManifestStatus.PROCESSING);
        manifest.setProcessingStartedAt(Instant.now());
        manifest.setAttemptCount(manifest.getAttemptCount() + 1);
        return videoManifestRepository.save(manifest);
    }

    @Override
    public VideoManifest markComplete(String id, VideoManifestCompleteRequest request) {
        VideoManifest manifest = getById(id);
        manifest.setStatus(VideoManifestStatus.READY);
        manifest.setMasterManifestS3Key(request.masterManifestS3Key());
        manifest.setDurationSeconds(request.durationSeconds());
        manifest.setRenditions(request.renditions().stream().map(this::toRendition).toList());
        manifest.setEncryptionKeyCiphertext(request.wrappedKey());
        manifest.setFailureReason(null);
        VideoManifest saved = videoManifestRepository.save(manifest);

        VideoTranscodeCompletedEvent event = new VideoTranscodeCompletedEvent(
                saved.getId(), saved.getSourceMediaId(), saved.getOwnerId(), saved.getOwnerEmail());
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(TopicNames.VIDEO_TRANSCODE_COMPLETED, event);
            return true;
        });
        return saved;
    }

    private VideoManifest.Rendition toRendition(RenditionRequest r) {
        return VideoManifest.Rendition.builder()
                .resolutionLabel(r.resolutionLabel())
                .width(r.width())
                .height(r.height())
                .bitrateKbps(r.bitrateKbps())
                .playlistS3Key(r.playlistS3Key())
                .build();
    }

    @Override
    public VideoManifest markFailed(String id, String reason) {
        VideoManifest manifest = getById(id);
        manifest.setStatus(VideoManifestStatus.FAILED);
        manifest.setFailureReason(reason);
        return videoManifestRepository.save(manifest);
    }

    @Override
    public VideoManifest retry(String id) {
        VideoManifest manifest = getById(id);
        if (manifest.getStatus() != VideoManifestStatus.FAILED) {
            throw new BadRequestException("Only a FAILED manifest can be retried: " + id);
        }
        manifest.setStatus(VideoManifestStatus.QUEUED);
        manifest.setFailureReason(null);
        VideoManifest saved = videoManifestRepository.save(manifest);

        VideoSourceReadyEvent event = new VideoSourceReadyEvent(
                saved.getId(), saved.getSourceMediaId(), saved.getOwnerId(), saved.getOwnerEmail(),
                null, saved.getStorageProvider().name(), null, null);
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(TopicNames.VIDEO_SOURCE_READY, event);
            return true;
        });
        return saved;
    }

    @Override
    public List<VideoManifest> findStuckProcessing(Instant threshold) {
        return videoManifestRepository.findByStatusAndProcessingStartedAtBefore(
                VideoManifestStatus.PROCESSING, threshold);
    }

    @Override
    public void reconcileStuck(VideoManifest manifest, int maxAttempts) {
        if (manifest.getAttemptCount() < maxAttempts) {
            log.warn("Manifest {} stuck in PROCESSING (attempt {}/{}), re-queueing.",
                    manifest.getId(), manifest.getAttemptCount(), maxAttempts);
            manifest.setStatus(VideoManifestStatus.QUEUED);
            VideoManifest saved = videoManifestRepository.save(manifest);
            VideoSourceReadyEvent event = new VideoSourceReadyEvent(
                    saved.getId(), saved.getSourceMediaId(), saved.getOwnerId(), saved.getOwnerEmail(),
                    null, saved.getStorageProvider().name(), null, null);
            kafkaTemplate.executeInTransaction(kt -> {
                kt.send(TopicNames.VIDEO_SOURCE_READY, event);
                return true;
            });
        } else {
            log.error("Manifest {} exceeded max attempts ({}), marking FAILED.", manifest.getId(), maxAttempts);
            manifest.setStatus(VideoManifestStatus.FAILED);
            manifest.setFailureReason("Exceeded max transcode attempts (" + maxAttempts + "); worker likely crashed mid-job.");
            videoManifestRepository.save(manifest);
        }
    }
}

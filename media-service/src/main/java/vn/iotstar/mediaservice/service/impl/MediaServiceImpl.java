package vn.iotstar.mediaservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.mediaservice.service.MediaStorageService;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderResolver;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.mediaservice.util.TopicNames;
import vn.iotstar.mediaservice.common.dto.MediaReadyEvent;
import vn.iotstar.mediaservice.common.dto.UploadRequestDto;
import vn.iotstar.mediaservice.common.dto.UploadResponseDto;
import vn.iotstar.mediaservice.exception.BadRequestException;
import vn.iotstar.mediaservice.exception.ForbiddenException;
import vn.iotstar.mediaservice.exception.ResourceNotFoundException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    /** Mặc định chỉ chấp nhận ảnh; nới rộng khi có nhu cầu thật cho loại nội dung khác. */
    private static final String ALLOWED_CONTENT_TYPE_PREFIX = "image/";

    /** 20MB — đủ cho ảnh chất lượng cao, chặn upload file khổng lồ chiếm dung lượng/bandwidth. */
    static final long MAX_UPLOAD_SIZE_BYTES = 20L * 1024 * 1024;

    private final MediaStorageService mediaStorageService;
    private final StorageProviderResolver storageProviderResolver;
    private final MediaRepository mediaRepository;
    private final KafkaTemplate<String, MediaReadyEvent> kafkaTemplate;

    @Transactional
    @Override
    public void processSuccessfulUpload(Media media) {
        if (media == null || media.getStatus() == MediaStatus.COMPLETED) {
            log.warn("Media with ID {} is null or already processed. Skipping.", media != null ? media.getId() : "null");
            return;
        }

        log.info("Processing successful upload for mediaId: {}", media.getId());
        media.setStatus(MediaStatus.COMPLETED);
        media.setCdnUrl(mediaStorageService.generateCdnUrl(media.getEffectiveStorageProvider(), media.getS3Key()));
        mediaRepository.save(media);
        sendMediaReadyEvent(new MediaReadyEvent(media.getId(), media.getOwnerId(), media.getCdnUrl()));
    }

    @Override
    public void markAsFailed(Media media) {
        log.warn("Marking mediaId {} as FAILED.", media.getId());
        mediaStorageService.deleteObjectQuietly(media.getEffectiveStorageProvider(), media.getS3Key());
        media.setStatus(MediaStatus.FAILED);
        mediaRepository.save(media);
    }

    @Override
    public boolean doesS3ObjectExist(Media media) {
        return mediaStorageService.doesObjectExist(media.getEffectiveStorageProvider(), media.getS3Key());
    }

    @Override
    public UploadResponseDto requestUploadUrl(UploadRequestDto request) {
        validateUploadRequest(request);
        try {
            // UUID sinh TRƯỚC khi tính S3 key: key phải namespaced theo mediaId
            // (media/{ownerId}/{mediaId}/{fileName}) để hai lần upload trùng tên file không bao
            // giờ ghi đè lẫn nhau.
            String mediaId = UUID.randomUUID().toString();
            String s3Key = createS3Key(request, mediaId);
            StorageProvider provider = storageProviderResolver.resolveForNewUpload();
            String resignUrl = mediaStorageService.generatePresignedUploadUrl(
                    provider, s3Key, request.contentType(), request.size());
            Media media = createMediaRecord(request, mediaId, s3Key, provider, currentUserEmail());
            return new UploadResponseDto(media.getId(), resignUrl);
        } catch (SdkException e) {
            log.error("Storage provider error: Failed to generate presigned URL. Check credentials and bucket configuration.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot generate upload URL.");
        } catch (Exception e) {
            log.error("An unexpected error occurred while requesting upload URL.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Allowlist content-type + giới hạn kích thước TRƯỚC khi cấp presigned URL — chặn client xin
     * URL cho loại file/kích thước không hợp lệ ngay từ đầu thay vì để storage backend từ chối lúc
     * upload thật.
     */
    private void validateUploadRequest(UploadRequestDto request) {
        String contentType = request.contentType();
        if (contentType == null || contentType.isBlank() || !contentType.startsWith(ALLOWED_CONTENT_TYPE_PREFIX)) {
            throw new BadRequestException(
                    "Unsupported content type: " + contentType + ". Only '" + ALLOWED_CONTENT_TYPE_PREFIX + "*' is allowed.");
        }
        Long size = request.size();
        if (size == null || size <= 0) {
            throw new BadRequestException("File size must be a positive number of bytes.");
        }
        if (size > MAX_UPLOAD_SIZE_BYTES) {
            throw new BadRequestException(
                    "File size " + size + " exceeds the maximum allowed " + MAX_UPLOAD_SIZE_BYTES + " bytes.");
        }
    }

    private String createS3Key(UploadRequestDto request, String mediaId) {
        return "media/" + request.ownerId() + "/" + mediaId + "/" + request.fileName();
    }

    protected Media createMediaRecord(UploadRequestDto request, String mediaId, String s3Key, StorageProvider provider, String ownerEmail) {
        Media media = new Media();
        media.setId(mediaId);
        media.setOwnerId(request.ownerId());
        media.setOwnerEmail(ownerEmail);
        media.setOriginalFileName(request.fileName());
        media.setStatus(MediaStatus.PENDING);
        media.setS3Key(s3Key);
        media.setStorageProvider(provider);
        media.setContentType(request.contentType());
        media.setSize(request.size());
        return mediaRepository.save(media);
    }

    @Override
    public void sendMediaReadyEvent(MediaReadyEvent event) {
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(TopicNames.SEND_STATUS_MEDIA, event);
            log.info("Sent MediaReadyEvent for mediaId: {} to Kafka topic 'media-ready'", event.mediaId());
            return true;
        });
    }

    @Override
    public Media getMediaByS3Key(String key) {
        return mediaRepository.findByS3Key(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found for S3 key: " + key));
    }

    @Override
    public Page<Media> getMyMedia(String ownerEmail, Pageable pageable) {
        return mediaRepository.findByOwnerEmail(ownerEmail, pageable);
    }

    @Override
    public Media getMediaById(String id, String requesterEmail, boolean isAdmin) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + id));
        assertOwnerOrAdmin(media, requesterEmail, isAdmin);
        return media;
    }

    @Override
    public Page<Media> getMediaByOwnerId(String ownerId, Pageable pageable) {
        return mediaRepository.findByOwnerId(ownerId, pageable);
    }

    @Override
    public void deleteMedia(String id, String requesterEmail, boolean isAdmin) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + id));
        assertOwnerOrAdmin(media, requesterEmail, isAdmin);

        // Hard-delete ngay lập tức — không thể thu hồi, đây là chủ đích của thao tác xoá. KHÔNG
        // nuốt lỗi ở đây (khác với deleteObjectQuietly ở markAsFailed): nếu xoá thất bại, exception
        // phải văng ra và chặn luôn việc soft-delete record — người dùng yêu cầu xoá cần biết ngay
        // là thao tác không hoàn tất, thay vì im lặng để lại record đã "xoá" trong khi object vẫn
        // còn tồn tại thật trên storage.
        mediaStorageService.deleteObject(media.getEffectiveStorageProvider(), media.getS3Key());

        media.setStatus(MediaStatus.DELETED);
        mediaRepository.save(media);
    }

    private void assertOwnerOrAdmin(Media media, String requesterEmail, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!Objects.equals(media.getOwnerEmail(), requesterEmail)) {
            throw new ForbiddenException("You do not have permission to access this media.");
        }
    }

    private String currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}

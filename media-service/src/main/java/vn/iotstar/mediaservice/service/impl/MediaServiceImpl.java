package vn.iotstar.mediaservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.utils.constants.TopicNames;
import vn.iotstar.utils.dto.MediaReadyEvent;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.ForbiddenException;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.time.Duration;
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

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.presigned-url-duration-minutes}")
    private long durationMinutes;

    private final MediaRepository mediaRepository;
    private final KafkaTemplate<String, MediaReadyEvent> kafkaTemplate;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    @Transactional
    @Override
    public void processSuccessfulUpload(Media media) {
        if (media == null || media.getStatus() == MediaStatus.COMPLETED) {
            log.warn("Media with ID {} is null or already processed. Skipping.", media != null ? media.getId() : "null");
            return;
        }

        log.info("Processing successful upload for mediaId: {}", media.getId());
        media.setStatus(MediaStatus.COMPLETED);
        media.setCdnUrl(generateCdnUrl(media.getS3Key()));
        mediaRepository.save(media);
        sendMediaReadyEvent(new MediaReadyEvent(media.getId(), media.getOwnerId(), media.getCdnUrl()));
    }

    @Override
    public void markAsFailed(Media media) {
        log.warn("Marking mediaId {} as FAILED.", media.getId());
        deleteS3ObjectQuietly(media.getS3Key());
        media.setStatus(MediaStatus.FAILED);
        mediaRepository.save(media);
    }

    /**
     * Dọn object mồ côi trên S3 trước khi đánh dấu record thất bại. Best-effort: lỗi xoá không
     * được chặn việc đánh dấu FAILED — record vẫn phải chuyển trạng thái để không bị cleanup job
     * quét lại vô hạn, object rác (nếu có) sẽ được xử lý bằng lifecycle policy của bucket.
     */
    private void deleteS3ObjectQuietly(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
        } catch (S3Exception e) {
            log.error("Failed to delete orphaned S3 object for key {}", s3Key, e);
        }
    }

    @Override
    public boolean doesS3ObjectExist(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
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
            String resignUrl = generateResignedUrl(s3Key, request.contentType(), request.size());
            Media media = createMediaRecord(request, mediaId, s3Key, currentUserEmail());
            return new UploadResponseDto(media.getId(), resignUrl);
        } catch (S3Exception e) {
            log.error("AWS S3 Error: Failed to generate presigned URL. Check IAM permissions and bucket configuration.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot generate upload URL.");
        } catch (Exception e) {
            log.error("An unexpected error occurred while requesting upload URL.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Allowlist content-type + giới hạn kích thước TRƯỚC khi cấp presigned URL — chặn client xin
     * URL cho loại file/kích thước không hợp lệ ngay từ đầu thay vì để S3 từ chối lúc upload thật.
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

    protected Media createMediaRecord(UploadRequestDto request, String mediaId, String s3Key, String ownerEmail) {
        Media media = new Media();
        media.setId(mediaId);
        media.setOwnerId(request.ownerId());
        media.setOwnerEmail(ownerEmail);
        media.setOriginalFileName(request.fileName());
        media.setStatus(MediaStatus.PENDING);
        media.setS3Key(s3Key);
        media.setContentType(request.contentType());
        media.setSize(request.size());
        return mediaRepository.save(media);
    }

    private String generateCdnUrl(String key) {
        return cdnBaseUrl + "/" + key;
    }

    /**
     * Ký cả {@code Content-Type}/{@code Content-Length} vào presigned PUT: SigV4 ký header nào thì
     * S3 ép header đó lúc upload thật, nên client upload sai type/size sẽ bị S3 từ chối bằng
     * signature mismatch thay vì âm thầm chấp nhận.
     */
    public String generateResignedUrl(String key, String contentType, long contentLength) {

        PutObjectPresignRequest resignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(durationMinutes))
                .putObjectRequest(por -> por
                                .bucket(bucketName)
                                .key(key)
                                .contentType(contentType)
                                .contentLength(contentLength))
                .build();

        PresignedPutObjectRequest resignedRequest = s3Presigner.presignPutObject(resignRequest);
        return resignedRequest.url().toString();
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

        // Hard-delete trên S3 ngay lập tức — không thể thu hồi, đây là chủ đích của thao tác xoá.
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(media.getS3Key())
                .build());

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

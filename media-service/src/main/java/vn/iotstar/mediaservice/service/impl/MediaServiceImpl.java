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
import vn.iotstar.mediaservice.service.VideoManifestService;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderResolver;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.mediaservice.util.TopicNames;
import vn.iotstar.mediaservice.common.dto.CompleteMultipartRequest;
import vn.iotstar.mediaservice.common.dto.MediaReadyEvent;
import vn.iotstar.mediaservice.common.dto.MultipartInitRequest;
import vn.iotstar.mediaservice.common.dto.MultipartInitResponse;
import vn.iotstar.mediaservice.common.dto.PartUrlResponse;
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

    private static final String IMAGE_PREFIX = "image/";
    private static final String VIDEO_PREFIX = "video/";

    /** 20MB — đủ cho ảnh chất lượng cao, chặn upload file khổng lồ chiếm dung lượng/bandwidth. */
    static final long MAX_UPLOAD_SIZE_BYTES_IMAGE = 20L * 1024 * 1024;

    /**
     * Trần cho video đi qua single-PUT (clip ngắn) — trình duyệt PUT một request GB-scale không
     * đáng tin cậy (mất kết nối giữa chừng là mất sạch, không resume). File lớn hơn phải đi qua
     * {@link #initiateMultipartUpload}.
     */
    static final long MAX_UPLOAD_SIZE_BYTES_VIDEO_SINGLE_PUT = 100L * 1024 * 1024;

    /** Trần cho luồng multipart — S3 giới hạn cứng 5 TiB/object, 10 000 part; 20GB là đủ rộng cho v1. */
    static final long MAX_MULTIPART_UPLOAD_SIZE_BYTES = 20L * 1024 * 1024 * 1024;

    /** Khớp giới hạn cứng của S3 cho một part (trừ part cuối, có thể nhỏ hơn). */
    private static final long MULTIPART_PART_SIZE_BYTES = 16L * 1024 * 1024;

    private final MediaStorageService mediaStorageService;
    private final StorageProviderResolver storageProviderResolver;
    private final MediaRepository mediaRepository;
    private final VideoManifestService videoManifestService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    @Override
    public void processSuccessfulUpload(Media media) {
        if (media == null || media.getStatus() == MediaStatus.COMPLETED) {
            log.warn("Media with ID {} is null or already processed. Skipping.", media != null ? media.getId() : "null");
            return;
        }

        log.info("Processing successful upload for mediaId: {}", media.getId());
        media.setStatus(MediaStatus.COMPLETED);

        if (isVideo(media.getContentType())) {
            // KHÔNG set cdnUrl cho video: URL phát phải được streaming-service ký theo từng
            // request (presigned GET + token), không được bake vào một document/event vĩnh viễn.
            // Xem thêm: media-service không còn là nguồn URL phát cuối cùng cho video.
            mediaRepository.save(media);
            videoManifestService.createQueuedFor(media);
        } else {
            // image/* — hoàn toàn không đổi so với trước: vẫn set cdnUrl + publish MediaReadyEvent
            // lên send-status-media.v1. BẮT BUỘC giữ nguyên: user-service's KafkaServiceImpl áp
            // MỌI event trên topic này thành cập nhật avatar không điều kiện — publish video vào
            // đây sẽ ghi đè avatar người dùng bằng URL video.
            media.setCdnUrl(mediaStorageService.generateCdnUrl(media.getEffectiveStorageProvider(), media.getS3Key()));
            mediaRepository.save(media);
            sendMediaReadyEvent(new MediaReadyEvent(media.getId(), media.getOwnerId(), media.getCdnUrl()));
        }
    }

    private boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith(VIDEO_PREFIX);
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
     * upload thật. Trần kích thước khác nhau theo loại nội dung (ảnh nhỏ, video single-PUT giới
     * hạn thấp hơn multipart rất nhiều — xem hằng số ở đầu lớp).
     */
    private void validateUploadRequest(UploadRequestDto request) {
        long maxAllowed = validateContentTypeAndGetCap(request.contentType(), MAX_UPLOAD_SIZE_BYTES_VIDEO_SINGLE_PUT);
        validateSize(request.size(), maxAllowed);
    }

    private long validateContentTypeAndGetCap(String contentType, long videoCap) {
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Content type must not be blank.");
        }
        if (contentType.startsWith(IMAGE_PREFIX)) {
            return MAX_UPLOAD_SIZE_BYTES_IMAGE;
        }
        if (contentType.startsWith(VIDEO_PREFIX)) {
            return videoCap;
        }
        throw new BadRequestException(
                "Unsupported content type: " + contentType + ". Only '" + IMAGE_PREFIX + "*' or '" + VIDEO_PREFIX + "*' is allowed.");
    }

    private void validateSize(Long size, long maxAllowed) {
        if (size == null || size <= 0) {
            throw new BadRequestException("File size must be a positive number of bytes.");
        }
        if (size > maxAllowed) {
            throw new BadRequestException(
                    "File size " + size + " exceeds the maximum allowed " + maxAllowed + " bytes.");
        }
    }

    private String createS3Key(UploadRequestDto request, String mediaId) {
        return s3Key(request.ownerId(), mediaId, request.fileName());
    }

    private String s3Key(String ownerId, String mediaId, String fileName) {
        return "media/" + ownerId + "/" + mediaId + "/" + fileName;
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

    // ---------- Multipart upload — bắt buộc cho video (single-PUT không đáng tin cậy ở quy mô GB) ----------

    @Override
    public MultipartInitResponse initiateMultipartUpload(MultipartInitRequest request) {
        long maxAllowed = validateContentTypeAndGetCap(request.contentType(), MAX_MULTIPART_UPLOAD_SIZE_BYTES);
        validateSize(request.size(), maxAllowed);

        String mediaId = UUID.randomUUID().toString();
        String s3Key = s3Key(request.ownerId(), mediaId, request.fileName());
        StorageProvider provider = storageProviderResolver.resolveForNewUpload();
        String uploadId = mediaStorageService.initiateMultipartUpload(provider, s3Key, request.contentType());

        Media media = new Media();
        media.setId(mediaId);
        media.setOwnerId(request.ownerId());
        media.setOwnerEmail(currentUserEmail());
        media.setOriginalFileName(request.fileName());
        media.setStatus(MediaStatus.PENDING);
        media.setS3Key(s3Key);
        media.setStorageProvider(provider);
        media.setContentType(request.contentType());
        media.setSize(request.size());
        media.setMultipartUploadId(uploadId);
        mediaRepository.save(media);

        int partCount = (int) Math.ceil((double) request.size() / MULTIPART_PART_SIZE_BYTES);
        return new MultipartInitResponse(mediaId, uploadId, partCount, MULTIPART_PART_SIZE_BYTES);
    }

    @Override
    public PartUrlResponse presignUploadPart(String mediaId, String uploadId, int partNumber) {
        Media media = getMultipartMediaOrThrow(mediaId, uploadId);
        String url = mediaStorageService.presignUploadPart(
                media.getEffectiveStorageProvider(), media.getS3Key(), uploadId, partNumber);
        return new PartUrlResponse(partNumber, url);
    }

    @Override
    public void completeMultipartUpload(CompleteMultipartRequest request) {
        Media media = getMultipartMediaOrThrow(request.mediaId(), request.uploadId());
        mediaStorageService.completeMultipartUpload(
                media.getEffectiveStorageProvider(), media.getS3Key(), request.uploadId(), request.parts());
        media.setMultipartUploadId(null);
        mediaRepository.save(media);
        // Không cần chờ S3 event/SQS ở đây: complete gọi thành công tức object đã tồn tại thật,
        // nên xử lý luôn thay vì đợi vòng SQS -> S3UploadEventListener như luồng single-PUT.
        processSuccessfulUpload(media);
    }

    @Override
    public void abortMultipartUpload(String mediaId, String uploadId) {
        Media media = getMultipartMediaOrThrow(mediaId, uploadId);
        mediaStorageService.abortMultipartUpload(media.getEffectiveStorageProvider(), media.getS3Key(), uploadId);
        mediaRepository.delete(media);
    }

    private Media getMultipartMediaOrThrow(String mediaId, String uploadId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
        if (!Objects.equals(media.getMultipartUploadId(), uploadId)) {
            throw new BadRequestException("uploadId does not match the multipart upload initiated for this media.");
        }
        return media;
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

package vn.iotstar.mediaservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
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
import vn.iotstar.mediaservice.util.TopicName;
import vn.iotstar.utils.dto.MediaReadyEvent;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

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
        media.setStatus(MediaStatus.FAILED);
        mediaRepository.save(media);
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
        try {
            String s3Key = createS3Key(request);
            String resignUrl = generateResignedUrl(s3Key);
            Media media = createMediaRecord(request,s3Key);
            return new UploadResponseDto(media.getId(), resignUrl);
        } catch (S3Exception e) {
            log.error("AWS S3 Error: Failed to generate presigned URL. Check IAM permissions and bucket configuration.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot generate upload URL.");
        } catch (Exception e) {
            log.error("An unexpected error occurred while requesting upload URL.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String createS3Key(UploadRequestDto request) {
        return request.ownerId() + "/" + request.fileName();
    }

    protected Media createMediaRecord(UploadRequestDto request, String s3Key) {
        Media media = new Media();
        media.setId(UUID.randomUUID().toString());
        media.setOwnerId(request.ownerId());
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

    public String generateResignedUrl(String key) {

        PutObjectPresignRequest resignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(durationMinutes))
                .putObjectRequest(por -> por
                                .bucket(bucketName)
                                .key(key))
                .build();

        PresignedPutObjectRequest resignedRequest = s3Presigner.presignPutObject(resignRequest);
        return resignedRequest.url().toString();
    }

    @Override
    public void sendMediaReadyEvent(MediaReadyEvent event) {
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(TopicName.SEND_STATUS_MEDIA, event);
            log.info("Sent MediaReadyEvent for mediaId: {} to Kafka topic 'media-ready'", event.mediaId());
            return true;
        });
    }

    @Override
    public Media getMediaByS3Key(String key) {
        return mediaRepository.findByS3Key(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found for S3 key: " + key));
    }
}
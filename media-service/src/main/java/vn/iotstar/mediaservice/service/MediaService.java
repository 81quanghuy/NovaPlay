package vn.iotstar.mediaservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.common.dto.MediaReadyEvent;
import vn.iotstar.mediaservice.common.dto.UploadRequestDto;
import vn.iotstar.mediaservice.common.dto.UploadResponseDto;

public interface MediaService {
    @Transactional
    void processSuccessfulUpload(Media media);

    /**
     * Đánh dấu record là FAILED. Trước đó xoá luôn object mồ côi (nếu có) trên S3 ứng với
     * {@code media.getS3Key()} — không thể thu hồi được, đây là chủ đích của cleanup job.
     */
    void markAsFailed(Media media);

    boolean doesS3ObjectExist(String key);

    UploadResponseDto requestUploadUrl(UploadRequestDto request);

    /**
     * Send media ready event to Kafka topic
     * @param mediaReadyEvent event data
     */
    void sendMediaReadyEvent(MediaReadyEvent mediaReadyEvent);

    /**
     * Get Media entity by S3 key
     * @param key S3 object key
     * @return list of Media entities
     */
    Media getMediaByS3Key(String key);

    /** Media của caller hiện tại (theo {@code ownerEmail}), có phân trang. */
    Page<Media> getMyMedia(String ownerEmail, Pageable pageable);

    /**
     * Chi tiết một media theo id.
     *
     * @throws vn.iotstar.mediaservice.exception.ResourceNotFoundException nếu không tồn tại
     * @throws vn.iotstar.mediaservice.exception.ForbiddenException nếu caller không phải chủ sở
     *         hữu và không phải admin
     */
    Media getMediaById(String id, String requesterEmail, boolean isAdmin);

    /** Media của một owner cụ thể, dùng cho endpoint admin-list. */
    Page<Media> getMediaByOwnerId(String ownerId, Pageable pageable);

    /**
     * Soft-delete record (đánh dấu {@link vn.iotstar.mediaservice.util.MediaStatus#DELETED}) rồi
     * hard-delete object trên S3 ngay lập tức — không thể thu hồi, đây là chủ đích.
     *
     * @throws vn.iotstar.mediaservice.exception.ResourceNotFoundException nếu không tồn tại
     * @throws vn.iotstar.mediaservice.exception.ForbiddenException nếu caller không phải chủ sở
     *         hữu và không phải admin
     */
    void deleteMedia(String id, String requesterEmail, boolean isAdmin);
}

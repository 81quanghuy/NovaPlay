package vn.iotstar.mediaservice.service;

import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.utils.dto.MediaReadyEvent;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

public interface MediaService {
    @Transactional
    void processSuccessfulUpload(Media media);

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
}

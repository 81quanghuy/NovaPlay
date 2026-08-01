package vn.iotstar.mediaservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.utils.dto.MediaReadyEvent;

/**
 * Lắng nghe sự kiện "S3 object created" mà S3 chuyển tiếp qua SQS sau khi client upload xong
 * bằng presigned URL, rồi đánh dấu {@link Media} tương ứng là COMPLETED.
 * <p>
 * Mặc định bật ({@code matchIfMissing = true}) vì prod luôn cần lắng nghe SQS thật. Chỉ tắt ở
 * dev ({@code aws.sqs.enabled=false}) vì SQS không được MinIO emulate — xem
 * {@link vn.iotstar.mediaservice.config.AwsConfig}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "aws.sqs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3UploadEventListener {

    private final ObjectMapper objectMapper;
    private final MediaService mediaService;

    @SqsListener(value = "${aws.sqs.queue-name}")
    public void handleS3UploadEvent(String messageJson) throws RuntimeException {
        try {
            log.info("Received message from SQS: {}", messageJson);

            JsonNode rootNode = objectMapper.readTree(messageJson);

            String key = java.net.URLDecoder.decode(
                    rootNode.at("/Records/0/s3/object/key").asText(), "UTF-8");

            Media media = mediaService.getMediaByS3Key(key);
            mediaService.processSuccessfulUpload(media);

            mediaService.sendMediaReadyEvent(
                    new MediaReadyEvent(media.getId(), media.getOwnerId(), media.getCdnUrl())
            );

        } catch (Exception e) {
            log.error("Error processing S3 upload event: ", e);
        }
    }
}

package vn.iotstar.mediaservice.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.utils.dto.MediaReadyEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaEventProducer {

    private final ObjectMapper objectMapper;
    private final MediaService mediaService;

    @SqsListener(value = "${aws.sqs.queue-name}")
    public void handleS3UploadEvent(String messageJson) {
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
            log.error("Failed to process message from SQS. Message will be re-queued.", e);
            throw new RuntimeException("Message processing failed, will be retried.", e);
        }
    }
}
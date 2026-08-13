package vn.iotstar.transcodingworker.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import vn.iotstar.transcodingworker.common.dto.RenditionRequest;
import vn.iotstar.transcodingworker.common.dto.VideoManifestCompleteRequest;
import vn.iotstar.transcodingworker.common.dto.VideoManifestDto;
import vn.iotstar.transcodingworker.common.dto.VideoManifestFailRequest;
import vn.iotstar.transcodingworker.common.dto.VideoSourceReadyEvent;
import vn.iotstar.transcodingworker.service.StorageObjectService;
import vn.iotstar.transcodingworker.service.client.MediaServiceClient;
import vn.iotstar.transcodingworker.storage.StorageProvider;
import vn.iotstar.transcodingworker.transcode.KeyWrapService;
import vn.iotstar.transcodingworker.transcode.RenditionOutput;
import vn.iotstar.transcodingworker.transcode.TranscodeResult;
import vn.iotstar.transcodingworker.transcode.TranscodeService;
import vn.iotstar.transcodingworker.util.TopicNames;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Nhặt {@code VideoSourceReadyEvent}, chạy pipeline transcode HLS đầu-cuối, báo kết quả về
 * media-service. Không tự động retry qua Kafka khi job thất bại (job có thể chạy hàng chục
 * phút — replay lại toàn bộ chỉ vì một lỗi thoáng qua gần cuối job là lãng phí); thất bại được
 * ghi nhận qua {@code PATCH /fail} và việc thử lại là hành động thủ công của admin
 * ({@code POST /retry}) hoặc tự động của job đối soát manifest kẹt bên media-service. Cơ chế
 * retry/DLT của Kafka (cấu hình ở {@code KafkaConsumerConfig}) chỉ là lưới an toàn cho lỗi hạ
 * tầng thực sự bất ngờ (ví dụ chính việc gọi {@code markFailed} cũng thất bại).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoTranscodeConsumer {

    private static final List<String> NOT_UPLOADED_FILES = List.of("segment.key", "keyinfo.txt");

    private final MediaServiceClient mediaServiceClient;
    private final StorageObjectService storageObjectService;
    private final TranscodeService transcodeService;
    private final KeyWrapService keyWrapService;

    @Value("${transcode.scratch-dir:/scratch}")
    private String scratchDirRoot;

    @KafkaListener(topics = TopicNames.VIDEO_SOURCE_READY, containerFactory = "kafkaListenerContainerFactory")
    public void handleVideoSourceReady(
            VideoSourceReadyEvent event,
            Acknowledgment ack,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {

        log.info("Received VideoSourceReadyEvent manifestId={} topic={} partition={} offset={}",
                event.manifestId(), topic, partition, offset);

        try {
            VideoManifestDto current = mediaServiceClient.getById(event.manifestId()).result();
            if (current == null) {
                log.error("Manifest {} not found — skipping (nothing to process).", event.manifestId());
                ack.acknowledge();
                return;
            }
            if ("PROCESSING".equals(current.status()) || "READY".equals(current.status())) {
                log.info("Manifest {} already {} — skipping (redelivered event).", event.manifestId(), current.status());
                ack.acknowledge();
                return;
            }

            runPipeline(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Transcode pipeline failed for manifestId={}", event.manifestId(), e);
            safelyMarkFailed(event.manifestId(), e);
            // Không rethrow: thất bại nghiệp vụ đã được ghi nhận vào manifest, ack để Kafka không
            // replay nguyên một job dài. Retry là hành động thủ công (POST /retry) hoặc tự động
            // của job đối soát bên media-service (PendingMediaCleanupJob).
            ack.acknowledge();
        }
    }

    private void runPipeline(VideoSourceReadyEvent event) throws IOException {
        mediaServiceClient.markProcessing(event.manifestId());

        StorageProvider provider = StorageProvider.fromName(event.storageProvider());
        Path jobDir = createScratchDir(event.manifestId());
        try {
            Path sourceFile = jobDir.resolve("source" + guessExtension(event.s3Key()));
            log.info("Downloading source {} to {}", event.s3Key(), sourceFile);
            storageObjectService.download(provider, event.s3Key(), sourceFile);

            Path outputDir = jobDir.resolve("hls");
            TranscodeResult result = transcodeService.transcode(sourceFile, outputDir);

            String prefix = "media/" + event.ownerId() + "/" + event.sourceMediaId() + "/hls/";
            uploadAll(provider, outputDir, prefix);

            String masterKey = prefix + "master.m3u8";
            List<RenditionRequest> renditions = result.renditions().stream()
                    .map(r -> toRenditionRequest(r, prefix, outputDir))
                    .toList();
            String wrappedKey = keyWrapService.wrap(result.rawEncryptionKey());

            VideoManifestCompleteRequest completeRequest = new VideoManifestCompleteRequest(
                    masterKey, result.durationSeconds(), renditions, wrappedKey);
            mediaServiceClient.markComplete(event.manifestId(), completeRequest);
            log.info("Manifest {} transcoded successfully: {} rendition(s).", event.manifestId(), renditions.size());
        } finally {
            cleanupQuietly(jobDir);
        }
    }

    private RenditionRequest toRenditionRequest(RenditionOutput output, String prefix, Path outputDir) {
        String relative = outputDir.relativize(output.playlistFile()).toString().replace('\\', '/');
        return new RenditionRequest(
                output.rung().label(), output.rung().width(), output.rung().height(),
                output.rung().bitrateKbps(), prefix + relative);
    }

    private void uploadAll(StorageProvider provider, Path outputDir, String prefix) throws IOException {
        try (Stream<Path> files = Files.walk(outputDir)) {
            List<Path> toUpload = files.filter(Files::isRegularFile)
                    .filter(p -> NOT_UPLOADED_FILES.stream().noneMatch(name -> p.getFileName().toString().equals(name)))
                    .toList();
            for (Path file : toUpload) {
                String relative = outputDir.relativize(file).toString().replace('\\', '/');
                String key = prefix + relative;
                String contentType = contentTypeFor(file);
                storageObjectService.upload(provider, key, file, contentType);
            }
        }
    }

    private String contentTypeFor(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".ts")) return "video/mp2t";
        return "application/octet-stream";
    }

    private String guessExtension(String s3Key) {
        int dot = s3Key.lastIndexOf('.');
        return dot >= 0 && dot > s3Key.lastIndexOf('/') ? s3Key.substring(dot) : ".mp4";
    }

    private Path createScratchDir(String manifestId) {
        try {
            Path dir = Path.of(scratchDirRoot, manifestId);
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create scratch dir for manifest " + manifestId, e);
        }
    }

    private void cleanupQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete scratch file {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk scratch dir {} for cleanup", dir, e);
        }
    }

    private void safelyMarkFailed(String manifestId, Exception cause) {
        try {
            String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            mediaServiceClient.markFailed(manifestId, new VideoManifestFailRequest(truncate(reason)));
        } catch (Exception markFailedError) {
            // Không nuốt hoàn toàn: nếu chính việc báo lỗi cũng thất bại (media-service sập), phải
            // để lộ ra để lưới an toàn DLT của Kafka bắt lấy thay vì âm thầm mất dấu manifest kẹt.
            log.error("Failed to mark manifest {} as FAILED after transcode error — rethrowing for DLT.", manifestId, markFailedError);
            throw new IllegalStateException("Could not report transcode failure for manifest " + manifestId, markFailedError);
        }
    }

    private String truncate(String reason) {
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}

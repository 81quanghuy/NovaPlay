package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import vn.iotstar.streamingservice.config.StorageClientConfig.ProviderClients;
import vn.iotstar.streamingservice.exception.ResourceNotFoundException;
import vn.iotstar.streamingservice.service.HlsStorageService;
import vn.iotstar.streamingservice.storage.StorageProvider;
import vn.iotstar.streamingservice.storage.StorageProviderProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HlsStorageServiceImpl implements HlsStorageService {

    private final Map<StorageProvider, ProviderClients> storageProviderClients;
    private final StorageProviderProperties properties;

    @Override
    public String readTextObject(StorageProvider provider, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.get(provider).getBucketName())
                .key(key)
                .build();
        try (ResponseInputStream<GetObjectResponse> in = clientsFor(provider).client().getObject(request)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            throw new ResourceNotFoundException("Object not found in storage: " + key);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read object " + key, e);
        }
    }

    @Override
    public String presignGetObject(StorageProvider provider, String key, Duration ttl) {
        StorageProviderProperties.ProviderConfig config = properties.get(provider);
        Duration cappedTtl = ttl.compareTo(Duration.ofMinutes(config.getPresignedUrlDurationMinutes())) > 0
                ? Duration.ofMinutes(config.getPresignedUrlDurationMinutes())
                : ttl;

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(cappedTtl)
                .getObjectRequest(gor -> gor.bucket(config.getBucketName()).key(key))
                .build();

        PresignedGetObjectRequest presigned = clientsFor(provider).presigner().presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private ProviderClients clientsFor(StorageProvider provider) {
        return storageProviderClients.get(provider);
    }
}

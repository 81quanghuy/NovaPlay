package vn.iotstar.mediaservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import vn.iotstar.mediaservice.config.StorageClientConfig.ProviderClients;
import vn.iotstar.mediaservice.service.MediaStorageService;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Implementation duy nhất của {@link MediaStorageService}: cả ba {@link StorageProvider} nói API
 * tương thích S3 nên chung một code path, chỉ khác client/bucket/cdn tra theo provider tham số.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3CompatibleStorageService implements MediaStorageService {

    private final Map<StorageProvider, ProviderClients> storageProviderClients;
    private final StorageProviderProperties properties;

    /**
     * Ký cả {@code Content-Type}/{@code Content-Length} vào presigned PUT: SigV4 ký header nào thì
     * S3 ép header đó lúc upload thật, nên client upload sai type/size sẽ bị S3 từ chối bằng
     * signature mismatch thay vì âm thầm chấp nhận.
     */
    @Override
    public String generatePresignedUploadUrl(StorageProvider provider, String key, String contentType, long contentLength) {
        S3Presigner presigner = clientsFor(provider).presigner();
        StorageProviderProperties.ProviderConfig config = properties.get(provider);

        PutObjectPresignRequest resignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(config.getPresignedUrlDurationMinutes()))
                .putObjectRequest(por -> por
                        .bucket(config.getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength))
                .build();

        PresignedPutObjectRequest resignedRequest = presigner.presignPutObject(resignRequest);
        return resignedRequest.url().toString();
    }

    @Override
    public boolean doesObjectExist(StorageProvider provider, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(properties.get(provider).getBucketName())
                    .key(key)
                    .build();
            clientsFor(provider).client().headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void deleteObject(StorageProvider provider, String key) {
        clientsFor(provider).client().deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.get(provider).getBucketName())
                .key(key)
                .build());
    }

    /**
     * Best-effort: lỗi xoá không được chặn caller (dọn object mồ côi trước khi đánh dấu FAILED).
     * Bắt {@link SdkException} (lớp cha chung của cả {@code S3Exception} lẫn
     * {@code SdkClientException}), KHÔNG chỉ {@code S3Exception}: sự cố mạng/client-side
     * (timeout, DNS, connection reset...) là {@code SdkClientException}, một nhánh anh em chứ
     * không phải subtype của {@code S3Exception} — bắt hẹp sẽ để lỗi mạng thoát ra ngoài.
     */
    @Override
    public void deleteObjectQuietly(StorageProvider provider, String key) {
        try {
            deleteObject(provider, key);
        } catch (SdkException e) {
            log.error("Failed to delete orphaned object for provider {} key {}", provider, key, e);
        }
    }

    @Override
    public String generateCdnUrl(StorageProvider provider, String key) {
        return properties.get(provider).getCdnBaseUrl() + "/" + key;
    }

    private ProviderClients clientsFor(StorageProvider provider) {
        return storageProviderClients.get(provider);
    }
}

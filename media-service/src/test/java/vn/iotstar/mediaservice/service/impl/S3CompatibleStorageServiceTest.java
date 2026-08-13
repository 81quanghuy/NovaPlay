package vn.iotstar.mediaservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import vn.iotstar.mediaservice.config.StorageClientConfig.ProviderClients;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderProperties;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3CompatibleStorageServiceTest {

    private static final String BUCKET = "novaplay-media-test";
    private static final String CDN_BASE_URL = "https://cdn.novaplay.vn";

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private S3CompatibleStorageService service;

    @BeforeEach
    void setUp() {
        StorageProviderProperties properties = new StorageProviderProperties();
        StorageProviderProperties.ProviderConfig config = properties.getAwsS3();
        config.setBucketName(BUCKET);
        config.setCdnBaseUrl(CDN_BASE_URL);
        config.setPresignedUrlDurationMinutes(15);

        Map<StorageProvider, ProviderClients> clients = Map.of(
                StorageProvider.AWS_S3, new ProviderClients(s3Client, s3Presigner));

        service = new S3CompatibleStorageService(clients, properties);
    }

    @Test
    @DisplayName("Content-Type và Content-Length được ký vào PutObjectRequest")
    void signsContentTypeAndContentLength() throws MalformedURLException {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.local/" + BUCKET + "/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        service.generatePresignedUploadUrl(StorageProvider.AWS_S3, "media/owner-1/m1/photo.jpg", "image/jpeg", 1024L);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());

        PutObjectRequest putObjectRequest = captor.getValue().putObjectRequest();
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/jpeg");
        assertThat(putObjectRequest.contentLength()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("doesObjectExist true khi headObject thành công, false khi NoSuchKeyException")
    void doesObjectExistChecksHeadObject() {
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThat(service.doesObjectExist(StorageProvider.AWS_S3, "missing-key")).isFalse();
    }

    @Test
    @DisplayName("deleteObject KHÔNG nuốt lỗi — văng ra ngoài")
    void deleteObjectPropagatesErrors() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> service.deleteObject(StorageProvider.AWS_S3, "key"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("deleteObjectQuietly nuốt S3Exception (best-effort)")
    void deleteObjectQuietlySwallowsS3Exception() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        service.deleteObjectQuietly(StorageProvider.AWS_S3, "key");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("deleteObjectQuietly cũng nuốt SdkClientException (lỗi mạng/client-side)")
    void deleteObjectQuietlySwallowsClientException() {
        // SdkClientException là nhánh anh em của S3Exception dưới SdkException, không phải subtype
        // — bắt hẹp "catch (S3Exception e)" sẽ để lỗi này thoát ra ngoài.
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("network blip"));

        service.deleteObjectQuietly(StorageProvider.AWS_S3, "key");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("generateCdnUrl nối cdnBaseUrl với key")
    void generateCdnUrlConcatenatesKey() {
        assertThat(service.generateCdnUrl(StorageProvider.AWS_S3, "media/owner-1/m1/photo.jpg"))
                .isEqualTo(CDN_BASE_URL + "/media/owner-1/m1/photo.jpg");
    }
}

package vn.iotstar.mediaservice.service.impl;

import dev.openfeature.sdk.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import vn.iotstar.mediaservice.config.StorageClientConfig.ProviderClients;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.service.MediaStorageService;
import vn.iotstar.mediaservice.service.VideoManifestService;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderProperties;
import vn.iotstar.mediaservice.storage.StorageProviderResolver;
import vn.iotstar.mediaservice.common.dto.UploadRequestDto;
import vn.iotstar.mediaservice.common.dto.UploadResponseDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test luồng upload thật đầu-cuối chỉ nhánh {@link StorageProvider#AWS_S3} (LocalStack
 * {@code Service.S3}): xin presigned URL từ {@link MediaServiceImpl}, PUT file thật lên URL đó
 * bằng {@link HttpClient} trần (không dùng AWS SDK phía test — đúng như một trình duyệt/mobile
 * client sẽ làm), rồi xác nhận {@link MediaServiceImpl#doesS3ObjectExist(Media)} thấy object.
 * Không nhân bản IT này cho CLOUDFLARE_R2/BACKBLAZE_B2: cả ba provider dùng chung
 * {@link S3CompatibleStorageService}, phần khác nhau giữa chúng chỉ là config binding
 * ({@link StorageProviderProperties}), không cần Docker để test.
 * <p>
 * Không cần Mongo/Kafka thật: {@link MediaRepository} và {@link KafkaTemplate} được mock bằng
 * Mockito vì hai path được test ({@code requestUploadUrl}, {@code doesS3ObjectExist}) không cần gì
 * khác ngoài S3 client/presigner thật.
 * <p>
 * Tự bỏ qua khi máy không chạy được Docker — xem {@code MovieRepositoryIT} để biết lý do dùng
 * đúng idiom {@code @EnabledIf("dockerAvailable")} này.
 */
@Testcontainers
@EnabledIf("dockerAvailable")
class S3UploadFlowIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final String BUCKET = "novaplay-media-it";

    /**
     * Khởi tạo có điều kiện: khác với {@code MongoDBContainer}, constructor của
     * {@link LocalStackContainer} gọi thẳng vào {@code DockerClientFactory} để dò Docker daemon —
     * nếu không có Docker, nó ném exception ngay lúc field static này được khởi tạo (tức là lúc
     * JUnit NẠP class để đọc {@code @EnabledIf}), TRƯỚC KHI điều kiện {@code @EnabledIf} kịp chạy
     * để bỏ qua test. Guard bằng {@link #dockerAvailable()} để field là {@code null} thay vì ném
     * exception khi không có Docker, giữ đúng chủ đích "tự bỏ qua" của {@code @EnabledIf}.
     */
    @Container
    static LocalStackContainer localstack = dockerAvailable()
            ? new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3)
            : null;

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private MediaServiceImpl mediaService;
    private MediaRepository mediaRepository;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());

        s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(credentials)
                .region(region)
                .forcePathStyle(true)
                .build();

        s3Presigner = S3Presigner.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        mediaRepository = mock(MediaRepository.class);
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        VideoManifestService videoManifestService = mock(VideoManifestService.class);

        StorageProviderProperties properties = new StorageProviderProperties();
        properties.getAwsS3().setBucketName(BUCKET);
        properties.getAwsS3().setCdnBaseUrl("http://cdn.local");
        properties.getAwsS3().setPresignedUrlDurationMinutes(15);
        Map<StorageProvider, ProviderClients> clients = Map.of(
                StorageProvider.AWS_S3, new ProviderClients(s3Client, s3Presigner));
        MediaStorageService mediaStorageService = new S3CompatibleStorageService(clients, properties);

        // Nhánh AWS_S3 duy nhất trong test này: cờ sống luôn trả "aws-s3", không cần flagd thật.
        Client openFeatureClient = mock(Client.class);
        when(openFeatureClient.getStringValue(anyString(), anyString())).thenReturn("aws-s3");
        StorageProviderResolver storageProviderResolver = new StorageProviderResolver(openFeatureClient);

        mediaService = new MediaServiceImpl(
                mediaStorageService, storageProviderResolver, mediaRepository, videoManifestService, kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        s3Client.close();
        s3Presigner.close();
    }

    @Test
    @DisplayName("xin presigned URL, PUT file thật lên, rồi doesS3ObjectExist thấy object")
    void requestUploadUrlThenPutThenObjectExists() throws Exception {
        byte[] content = "hello novaplay".getBytes(StandardCharsets.UTF_8);
        UploadRequestDto request = new UploadRequestDto(
                "owner-1", "photo.jpg", "image/jpeg", (long) content.length);

        UploadResponseDto response = mediaService.requestUploadUrl(request);
        assertThat(response.presignedUrl()).isNotBlank();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(response.presignedUrl()))
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<Void> putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.discarding());

        assertThat(putResponse.statusCode()).as("PUT lên presigned URL phải thành công").isEqualTo(200);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        Media savedMedia = captor.getValue();

        assertThat(savedMedia.getId()).isEqualTo(response.mediaId());
        assertThat(savedMedia.getS3Key())
                .isEqualTo("media/owner-1/" + savedMedia.getId() + "/photo.jpg");

        assertThat(mediaService.doesS3ObjectExist(savedMedia)).isTrue();

        Media notUploaded = new Media();
        notUploaded.setS3Key("media/owner-1/does-not-exist/photo.jpg");
        notUploaded.setStorageProvider(StorageProvider.AWS_S3);
        assertThat(mediaService.doesS3ObjectExist(notUploaded)).isFalse();
    }
}

package vn.iotstar.mediaservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderProperties;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * Thay thế {@code AwsConfig} cũ: trước đây chỉ MỘT trong hai bean {@code S3Client}/{@code S3Presigner}
 * (MinIO-dev hoặc AWS-prod) được đăng ký, chọn tĩnh lúc khởi động qua {@code @ConditionalOnExpression}
 * dựa trên sự hiện diện của {@code aws.endpoint.url}. Giờ provider active có thể đổi lúc chạy qua
 * OpenFeature ({@code StorageProviderResolver}), nên không còn "một bean đúng" nữa — cả ba client
 * pair (AWS_S3/CLOUDFLARE_R2/BACKBLAZE_B2) phải cùng tồn tại, build sẵn lúc khởi động (rẻ, không có
 * I/O mạng lúc tạo {@link S3Client}/{@link S3Presigner}), tra cứu theo {@link StorageProvider} lúc gọi.
 */
@Configuration
@RequiredArgsConstructor
public class StorageClientConfig {

    private final StorageProviderProperties properties;

    @Bean
    public Map<StorageProvider, ProviderClients> storageProviderClients() {
        Map<StorageProvider, ProviderClients> clients = new EnumMap<>(StorageProvider.class);
        for (StorageProvider provider : StorageProvider.values()) {
            clients.put(provider, buildClients(provider, properties.get(provider)));
        }
        return clients;
    }

    private ProviderClients buildClients(StorageProvider provider, StorageProviderProperties.ProviderConfig config) {
        Region region = Region.of(config.getRegion());
        // AWS_S3 luôn dùng DefaultCredentialsProvider: ở dev nó đọc AWS_ACCESS_KEY_ID/SECRET của
        // MinIO từ biến môi trường (docker-compose), ở prod nó lấy từ IAM role/instance profile —
        // giữ đúng hành vi của AwsConfig cũ. R2/B2 không phải tài khoản AWS thật nên phải dùng
        // access key/secret tĩnh cấu hình riêng cho từng provider.
        AwsCredentialsProvider credentialsProvider = provider == StorageProvider.AWS_S3
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        if (StringUtils.hasText(config.getEndpoint())) {
            URI endpoint = URI.create(config.getEndpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        if (config.isForcePathStyle()) {
            // S3Presigner không có forcePathStyle() trực tiếp như S3ClientBuilder — path-style
            // phải bật qua S3Configuration (giống AwsConfig cũ).
            clientBuilder.forcePathStyle(true);
            presignerBuilder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return new ProviderClients(clientBuilder.build(), presignerBuilder.build());
    }

    public record ProviderClients(S3Client client, S3Presigner presigner) {}
}

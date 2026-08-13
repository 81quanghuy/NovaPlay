package vn.iotstar.transcodingworker.config;

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
import vn.iotstar.transcodingworker.storage.StorageProvider;
import vn.iotstar.transcodingworker.storage.StorageProviderProperties;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * Bản sao thu gọn của media-service's {@code StorageClientConfig}: chỉ build {@link S3Client}
 * (đọc/ghi object bằng credentials thật), không có {@code S3Presigner} — worker không bao giờ
 * cấp URL cho trình duyệt.
 */
@Configuration
@RequiredArgsConstructor
public class StorageClientConfig {

    private final StorageProviderProperties properties;

    @Bean
    public Map<StorageProvider, S3Client> storageClients() {
        Map<StorageProvider, S3Client> clients = new EnumMap<>(StorageProvider.class);
        for (StorageProvider provider : StorageProvider.values()) {
            clients.put(provider, buildClient(provider, properties.get(provider)));
        }
        return clients;
    }

    private S3Client buildClient(StorageProvider provider, StorageProviderProperties.ProviderConfig config) {
        Region region = Region.of(config.getRegion());
        AwsCredentialsProvider credentialsProvider = provider == StorageProvider.AWS_S3
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));

        S3ClientBuilder builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        if (config.isForcePathStyle()) {
            builder.forcePathStyle(true);
        }
        return builder.build();
    }
}

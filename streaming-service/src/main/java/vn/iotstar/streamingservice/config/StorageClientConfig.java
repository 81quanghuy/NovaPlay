package vn.iotstar.streamingservice.config;

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
import vn.iotstar.streamingservice.storage.StorageProvider;
import vn.iotstar.streamingservice.storage.StorageProviderProperties;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * Bản sao của media-service's {@code StorageClientConfig}, nhưng streaming-service cần CẢ HAI:
 * {@code S3Client} để tự đọc nội dung playlist (.m3u8, text nhỏ) server-side lúc viết lại URI, và
 * {@code S3Presigner} để ký GET URL cho segment — trình duyệt tải segment thẳng từ storage, không
 * qua service này.
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
        AwsCredentialsProvider credentialsProvider = provider == StorageProvider.AWS_S3
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));

        S3ClientBuilder clientBuilder = S3Client.builder().region(region).credentialsProvider(credentialsProvider);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder().region(region).credentialsProvider(credentialsProvider);

        if (StringUtils.hasText(config.getEndpoint())) {
            URI endpoint = URI.create(config.getEndpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        if (config.isForcePathStyle()) {
            clientBuilder.forcePathStyle(true);
            presignerBuilder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return new ProviderClients(clientBuilder.build(), presignerBuilder.build());
    }

    public record ProviderClients(S3Client client, S3Presigner presigner) {}
}

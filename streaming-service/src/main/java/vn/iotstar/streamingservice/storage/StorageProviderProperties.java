package vn.iotstar.streamingservice.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "storage.providers")
@Component
@Getter
@Setter
public class StorageProviderProperties {

    private ProviderConfig awsS3 = new ProviderConfig();
    private ProviderConfig cloudflareR2 = new ProviderConfig();
    private ProviderConfig backblazeB2 = new ProviderConfig();

    public ProviderConfig get(StorageProvider provider) {
        return switch (provider) {
            case AWS_S3 -> awsS3;
            case CLOUDFLARE_R2 -> cloudflareR2;
            case BACKBLAZE_B2 -> backblazeB2;
        };
    }

    @Getter
    @Setter
    public static class ProviderConfig {
        private String region;
        private String endpoint;
        private String bucketName;
        private String accessKeyId;
        private String secretAccessKey;
        private boolean forcePathStyle;
        /** TTL của presigned GET cấp cho segment — trần tuyệt đối, còn lại theo playback token. */
        private long presignedUrlDurationMinutes = 360;
    }
}

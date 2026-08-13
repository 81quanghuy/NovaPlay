package vn.iotstar.mediaservice.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình riêng cho từng {@link StorageProvider}, binding từ {@code storage.providers.*} (kebab-case
 * relaxed binding chuẩn Spring Boot: {@code aws-s3} -> {@code awsS3}, v.v). Xem
 * {@code application-dev.yml}/{@code application-prod.yml} cho giá trị cụ thể từng môi trường.
 */
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
        /** Trống/absent với AWS S3 thật; bắt buộc với MinIO/R2/B2. */
        private String endpoint;
        private String bucketName;
        private String cdnBaseUrl;
        /** Trống với AWS_S3 (dùng {@code DefaultCredentialsProvider} — IAM role ở prod). */
        private String accessKeyId;
        private String secretAccessKey;
        private boolean forcePathStyle;
        private long presignedUrlDurationMinutes = 15;
    }
}

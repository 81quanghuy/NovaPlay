package vn.iotstar.mediaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Tách riêng khỏi {@code StorageClientConfig}: SQS không thuộc phạm vi chọn storage provider (S3 vs
 * R2 vs B2) — nó chỉ nhận sự kiện "object created" từ AWS S3 thật, không được MinIO/R2/B2 emulate,
 * nên client này luôn trỏ vào AWS thật bất kể storage provider đang active là gì. Xem
 * {@link vn.iotstar.mediaservice.consumer.S3UploadEventListener}.
 */
@Configuration
public class SqsConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

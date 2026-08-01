package vn.iotstar.mediaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

/**
 * {@code aws.endpoint.url} chỉ được set ở dev, trỏ vào MinIO chạy local (xem
 * {@code application-dev.yml}); ở prod nó vắng mặt nên bean AWS thật được kích hoạt thay vì
 * bean trỏ endpoint tuỳ chỉnh. Cả hai bean của cùng một kiểu (S3Client/S3Presigner) không thể
 * cùng tồn tại — {@code @ConditionalOnExpression} đảm bảo đúng một cái được đăng ký, nên không
 * cần {@code @Primary}.
 * <p>
 * Dùng {@code DefaultCredentialsProvider} thay vì static access key/secret: ở dev nó đọc từ biến
 * môi trường MinIO (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY do docker-compose set), ở prod nó lấy
 * từ IAM role/instance profile — không có credential nào được commit vào code hay config.
 */
@Configuration
public class AwsConfig {

    private static final String HAS_ENDPOINT_OVERRIDE =
            "#{T(org.springframework.util.StringUtils).hasText('${aws.endpoint.url:}')}";
    private static final String NO_ENDPOINT_OVERRIDE =
            "#{!T(org.springframework.util.StringUtils).hasText('${aws.endpoint.url:}')}";

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.endpoint.url:}")
    private String endpointUrl;

    /** Dùng ở dev: trỏ S3Client vào MinIO. forcePathStyle bắt buộc vì MinIO không hỗ trợ virtual-hosted-style. */
    @Bean
    @ConditionalOnExpression(HAS_ENDPOINT_OVERRIDE)
    public S3Client minioS3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .endpointOverride(URI.create(endpointUrl))
                .forcePathStyle(true)
                .build();
    }

    /** Dùng ở prod: S3 thật, không override endpoint. */
    @Bean
    @ConditionalOnExpression(NO_ENDPOINT_OVERRIDE)
    public S3Client awsS3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Dùng ở dev: presigned URL phải trỏ vào MinIO chứ không phải S3 thật. S3Presigner.Builder
     * không có forcePathStyle() trực tiếp như S3ClientBuilder — path-style phải bật qua
     * S3Configuration.
     */
    @Bean
    @ConditionalOnExpression(HAS_ENDPOINT_OVERRIDE)
    public S3Presigner minioS3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .endpointOverride(URI.create(endpointUrl))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /** Dùng ở prod: presigned URL trỏ vào S3 thật, không override endpoint. */
    @Bean
    @ConditionalOnExpression(NO_ENDPOINT_OVERRIDE)
    public S3Presigner awsS3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * SQS không được MinIO emulate ở dev (xem {@code aws.sqs.enabled=false} và
     * {@link vn.iotstar.mediaservice.consumer.S3UploadEventListener}), nên client này luôn trỏ
     * vào AWS thật — không cần bean điều kiện hay endpoint override.
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

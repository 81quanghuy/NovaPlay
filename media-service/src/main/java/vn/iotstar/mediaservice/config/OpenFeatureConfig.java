package vn.iotstar.mediaservice.config;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.iotstar.mediaservice.service.client.ConfigServiceClient;

import java.util.Map;

/**
 * Nơi DUY NHẤT trong tính năng này còn dùng {@code @ConditionalOnProperty} — nhưng nó chọn OpenFeature
 * đang nói chuyện với NGUỒN cờ nào (config-service thật hay in-memory cho dev đơn giản), KHÔNG phải
 * chọn storage provider đang active. Hai việc đó là hai mối quan tâm khác nhau: chọn provider
 * (S3/R2/B2) luôn đi qua {@link vn.iotstar.mediaservice.storage.StorageProviderResolver} gọi
 * OpenFeature API lúc runtime — annotation ở đây chỉ quyết định OpenFeatureAPI lấy dữ liệu cờ từ
 * đâu, một quyết định hạ tầng vẫn hợp lý để cố định lúc khởi động (giống việc chọn DB driver).
 */
@Configuration
public class OpenFeatureConfig {

    /** Nguồn cờ thật: đọc từ MongoDB của config-service qua Feign — xem ConfigServiceFeatureProvider. */
    @Bean
    @ConditionalOnProperty(prefix = "feature-flags", name = "provider", havingValue = "config-service", matchIfMissing = true)
    public FeatureProvider configServiceFeatureProvider(ConfigServiceClient configServiceClient) {
        return new ConfigServiceFeatureProvider(configServiceClient);
    }

    /** Dùng cho local dev khi không muốn chạy docker-compose (mongodb + config-service) — cờ cố định theo config Spring. */
    @Bean
    @ConditionalOnProperty(prefix = "feature-flags", name = "provider", havingValue = "in-memory")
    public FeatureProvider inMemoryFeatureProvider(
            @Value("${feature-flags.default-storage-provider:aws-s3}") String defaultVariant) {
        return new InMemoryProvider(Map.of(
                "media-storage-provider", Flag.builder()
                        .variant("aws-s3", "aws-s3")
                        .variant("cloudflare-r2", "cloudflare-r2")
                        .variant("backblaze-b2", "backblaze-b2")
                        .defaultVariant(defaultVariant)
                        .build()));
    }

    @Bean
    public Client openFeatureClient(FeatureProvider featureProvider) {
        // setProvider (KHÔNG PHẢI setProviderAndWait): đăng ký bất đồng bộ để startup của
        // media-service không bao giờ bị chặn/fail chỉ vì config-service tạm thời chưa sẵn sàng.
        // Theo spec OpenFeature, evaluation gọi trước khi provider READY (hoặc khi provider ở
        // trạng thái NOT_READY/FATAL) trả về default value do caller cung cấp, không throw — đúng
        // tính chất resilience cần cho trường hợp config-service down (xem StorageProviderResolver).
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProvider(featureProvider);
        return api.getClient();
    }
}

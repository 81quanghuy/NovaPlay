package vn.iotstar.streamingservice.config;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.iotstar.streamingservice.service.client.ConfigServiceClient;
import vn.iotstar.streamingservice.util.DeliveryMode;

import java.util.Map;

/**
 * Chọn NGUỒN cờ (config-service thật hay in-memory cho dev), KHÔNG phải chọn delivery mode đang
 * active — mode luôn được hỏi lúc runtime qua {@code CachedDeliveryModeResolver}. Bản sao pattern
 * của media-service's {@code OpenFeatureConfig}.
 */
@Configuration
public class OpenFeatureConfig {

    /** Nguồn cờ thật: đọc từ MongoDB của config-service qua Feign — xem ConfigServiceFeatureProvider. */
    @Bean
    @ConditionalOnProperty(prefix = "feature-flags", name = "provider", havingValue = "config-service", matchIfMissing = true)
    public FeatureProvider configServiceFeatureProvider(ConfigServiceClient configServiceClient) {
        return new ConfigServiceFeatureProvider(configServiceClient);
    }

    /** Dùng cho local dev khi không muốn chạy config-service — cờ cố định theo config Spring. */
    @Bean
    @ConditionalOnProperty(prefix = "feature-flags", name = "provider", havingValue = "in-memory")
    public FeatureProvider inMemoryFeatureProvider(
            @Value("${feature-flags.default-delivery-mode:direct}") String defaultVariant) {
        return new InMemoryProvider(Map.of(
                DeliveryMode.FLAG_KEY, Flag.builder()
                        .variant(DeliveryMode.DIRECT.flagVariant(), DeliveryMode.DIRECT.flagVariant())
                        .variant(DeliveryMode.CLOUDFRONT.flagVariant(), DeliveryMode.CLOUDFRONT.flagVariant())
                        .defaultVariant(defaultVariant)
                        .build()));
    }

    @Bean
    public Client openFeatureClient(FeatureProvider featureProvider) {
        // setProvider (KHÔNG PHẢI setProviderAndWait): đăng ký bất đồng bộ để startup của
        // streaming-service không bao giờ bị chặn/fail chỉ vì config-service tạm thời chưa sẵn
        // sàng. Theo spec OpenFeature, evaluation gọi trước khi provider READY trả về default
        // value do caller cung cấp, không throw — đúng tính chất fail-safe cần cho playback.
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProvider(featureProvider);
        return api.getClient();
    }
}

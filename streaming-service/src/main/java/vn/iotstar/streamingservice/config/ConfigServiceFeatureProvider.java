package vn.iotstar.streamingservice.config;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.iotstar.streamingservice.service.client.ConfigServiceClient;
import vn.iotstar.streamingservice.service.client.dto.ConfigFlagDto;

/**
 * {@link FeatureProvider} đọc cờ từ config-service (một MongoDB document mỗi key) qua
 * {@link ConfigServiceClient} — bản sao pattern của media-service (không có shared lib). Sửa giá
 * trị cờ = sửa document trong MongoDB của config-service, không cần redeploy streaming-service.
 * <p>
 * MỌI lỗi (404 chưa cấu hình cờ, timeout, circuit OPEN khi config-service down) đều rơi vào
 * {@code fallbackStringEvaluation}, không bao giờ throw ra ngoài. Với cờ delivery mode, default là
 * {@code direct} — nghĩa là config-service chết chỉ làm mất lợi ích CDN, KHÔNG BAO GIỜ làm hỏng
 * playback. Đây là fail-safe, cố ý ngược với entitlement (fail-closed) vì đây không phải quyết
 * định bảo mật.
 * <p>
 * Chỉ {@code getStringEvaluation} có ý nghĩa thật: codebase chỉ dùng flag kiểu String. Bốn method
 * kiểu khác bắt buộc override vì là abstract của {@link FeatureProvider}, trả thẳng
 * {@code defaultValue}, không gọi mạng.
 */
@RequiredArgsConstructor
@Slf4j
public class ConfigServiceFeatureProvider implements FeatureProvider {

    private final ConfigServiceClient configServiceClient;

    @Override
    public Metadata getMetadata() {
        return () -> "config-service";
    }

    @CircuitBreaker(name = "configService", fallbackMethod = "fallbackStringEvaluation")
    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        ConfigFlagDto flag = configServiceClient.getFlag(key);
        return ProviderEvaluation.<String>builder().value(flag.value()).build();
    }

    /**
     * Không ném lỗi: fallback ném exception sẽ phá vỡ hợp đồng "luôn có giá trị" mà
     * {@code CachedDeliveryModeResolver} phụ thuộc vào. Package-private (không {@code private}) để
     * unit test gọi thẳng được.
     */
    ProviderEvaluation<String> fallbackStringEvaluation(String key, String defaultValue,
                                                        EvaluationContext ctx, Throwable ex) {
        log.warn("Failed to fetch flag '{}' from config-service, falling back to default '{}': {}",
                key, defaultValue, ex.toString());
        return ProviderEvaluation.<String>builder().value(defaultValue).build();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return ProviderEvaluation.<Boolean>builder().value(defaultValue).build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return ProviderEvaluation.<Integer>builder().value(defaultValue).build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return ProviderEvaluation.<Double>builder().value(defaultValue).build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return ProviderEvaluation.<Value>builder().value(defaultValue).build();
    }
}

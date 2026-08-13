package vn.iotstar.mediaservice.config;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.iotstar.mediaservice.service.client.ConfigServiceClient;
import vn.iotstar.mediaservice.service.client.dto.ConfigFlagDto;

/**
 * {@link FeatureProvider} đọc cờ từ config-service (một MongoDB document mỗi key) qua
 * {@link ConfigServiceClient} — nguồn cờ thật cho toggle storage provider, thay cho flagd. Sửa
 * giá trị cờ = sửa document trong MongoDB của config-service, không cần redeploy media-service.
 * <p>
 * Độ trễ của lời gọi Feign bị chặn cứng bởi {@code feign.client.config.config-service.read-timeout}
 * (xem application-dev.yml/application-prod.yml); {@code @CircuitBreaker} thêm khả năng "fail
 * fast" sau nhiều lần lỗi liên tiếp thay vì cứ chờ hết timeout mỗi lần — cùng pattern với
 * {@code UserController#changeAvatar} gọi media-service. MỌI lỗi (404 chưa cấu hình, timeout,
 * circuit OPEN khi service down) đều rơi vào {@code fallbackStringEvaluation}, không bao giờ
 * throw ra ngoài — giữ đúng tính chất "OpenFeature luôn có fallback" để config-service chậm/down
 * không làm treo {@code requestUploadUrl}.
 * <p>
 * Chỉ implement {@code getStringEvaluation} có ý nghĩa thật: codebase hiện chỉ dùng flag kiểu
 * String (xem {@code StorageProviderResolver}). Bốn method kiểu khác bắt buộc phải override vì là
 * abstract method của {@link FeatureProvider}, nhưng không có nhu cầu dùng nên trả thẳng
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
     * Không ném lỗi: fallback ném exception sẽ chỉ đổi nhãn lỗi chứ không làm dịu sự cố, và ở đây
     * còn phá vỡ hẳn hợp đồng "luôn có giá trị" mà {@code StorageProviderResolver} phụ thuộc vào.
     * Package-private (không {@code private}) để unit test gọi thẳng được — hành vi AOP thật của
     * {@code @CircuitBreaker} (tự động route exception vào đây) do resilience4j đảm bảo, không
     * cần dựng Spring context chỉ để re-verify lại behavior của chính thư viện.
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

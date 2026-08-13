package vn.iotstar.mediaservice.config;

import dev.openfeature.sdk.ProviderEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.mediaservice.service.client.ConfigServiceClient;
import vn.iotstar.mediaservice.service.client.dto.ConfigFlagDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Hành vi AOP thật của {@code @CircuitBreaker} (tự route exception từ
 * {@link ConfigServiceFeatureProvider#getStringEvaluation} sang
 * {@link ConfigServiceFeatureProvider#fallbackStringEvaluation}) là trách nhiệm của resilience4j,
 * không cần dựng Spring context để re-verify lại — test này verify logic của từng nhánh trực tiếp.
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceFeatureProviderTest {

    @Mock
    private ConfigServiceClient configServiceClient;

    private ConfigServiceFeatureProvider provider;

    @Test
    @DisplayName("getMetadata trả tên 'config-service'")
    void metadataName() {
        provider = new ConfigServiceFeatureProvider(configServiceClient);
        assertThat(provider.getMetadata().getName()).isEqualTo("config-service");
    }

    @Test
    @DisplayName("getStringEvaluation trả đúng giá trị config-service trả về")
    void returnsFetchedValueOnSuccess() {
        provider = new ConfigServiceFeatureProvider(configServiceClient);
        when(configServiceClient.getFlag("media-storage-provider"))
                .thenReturn(new ConfigFlagDto("media-storage-provider", "cloudflare-r2"));

        ProviderEvaluation<String> result = provider.getStringEvaluation(
                "media-storage-provider", "aws-s3", null);

        assertThat(result.getValue()).isEqualTo("cloudflare-r2");
    }

    @Test
    @DisplayName("fallbackStringEvaluation trả về defaultValue, không throw")
    void fallbackReturnsDefaultValue() {
        provider = new ConfigServiceFeatureProvider(configServiceClient);

        ProviderEvaluation<String> result = provider.fallbackStringEvaluation(
                "media-storage-provider", "aws-s3", null, new RuntimeException("config-service down"));

        assertThat(result.getValue()).isEqualTo("aws-s3");
    }

    @Test
    @DisplayName("getBooleanEvaluation/getIntegerEvaluation/getDoubleEvaluation/getObjectEvaluation trả defaultValue, không gọi mạng")
    void unusedFlagTypesReturnDefaultWithoutNetworkCall() {
        provider = new ConfigServiceFeatureProvider(configServiceClient);

        assertThat(provider.getBooleanEvaluation("k", true, null).getValue()).isTrue();
        assertThat(provider.getIntegerEvaluation("k", 42, null).getValue()).isEqualTo(42);
        assertThat(provider.getDoubleEvaluation("k", 1.5, null).getValue()).isEqualTo(1.5);
        org.mockito.Mockito.verifyNoInteractions(configServiceClient);
    }
}

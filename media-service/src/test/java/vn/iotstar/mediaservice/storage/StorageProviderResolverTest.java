package vn.iotstar.mediaservice.storage;

import dev.openfeature.sdk.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageProviderResolverTest {

    @Mock
    private Client openFeatureClient;

    private StorageProviderResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StorageProviderResolver(openFeatureClient);
        ReflectionTestUtils.setField(resolver, "defaultVariant", "aws-s3");
    }

    @Test
    @DisplayName("variant hợp lệ được map đúng sang StorageProvider tương ứng")
    void mapsKnownVariantToProvider() {
        when(openFeatureClient.getStringValue(anyString(), anyString())).thenReturn("cloudflare-r2");

        assertThat(resolver.resolveForNewUpload()).isEqualTo(StorageProvider.CLOUDFLARE_R2);
    }

    @Test
    @DisplayName("variant lạ (flagd cấu hình sai) fallback về AWS_S3 thay vì ném lỗi")
    void unknownVariantFallsBackToAwsS3() {
        when(openFeatureClient.getStringValue(anyString(), anyString())).thenReturn("some-unknown-variant");

        assertThat(resolver.resolveForNewUpload()).isEqualTo(StorageProvider.AWS_S3);
    }

    @Test
    @DisplayName("gọi getStringValue với đúng flag key và default-variant cấu hình")
    void queriesExpectedFlagKeyAndDefault() {
        when(openFeatureClient.getStringValue(any(), any())).thenReturn("aws-s3");

        resolver.resolveForNewUpload();

        org.mockito.Mockito.verify(openFeatureClient).getStringValue("media-storage-provider", "aws-s3");
    }
}

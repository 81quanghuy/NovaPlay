package vn.iotstar.mediaservice.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Backend lưu trữ object mà media-service có thể dùng. Cả ba đều nói API tương thích S3 (SigV4,
 * presigned PUT, path-style access) nên dùng chung một {@link software.amazon.awssdk.services.s3.S3Client}/
 * {@link software.amazon.awssdk.services.s3.presigner.S3Presigner} — chỉ khác endpoint/credentials/bucket,
 * xem {@code StorageClientConfig}.
 */
@Slf4j
public enum StorageProvider {
    AWS_S3("aws-s3"),
    CLOUDFLARE_R2("cloudflare-r2"),
    BACKBLAZE_B2("backblaze-b2");

    private final String flagVariant;

    StorageProvider(String flagVariant) {
        this.flagVariant = flagVariant;
    }

    public String flagVariant() {
        return flagVariant;
    }

    /**
     * Variant lạ (flagd cấu hình sai, hoặc cờ đang trỏ vào một variant chưa được media-service biết
     * tới) fallback về {@link #AWS_S3} thay vì ném exception — an toàn hơn cho việc cấp presigned
     * URL: sai lệch cấu hình cờ không được phép làm sập luồng upload.
     */
    public static StorageProvider fromFlagVariant(String variant) {
        return Arrays.stream(values())
                .filter(provider -> provider.flagVariant.equals(variant))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown storage provider flag variant '{}', falling back to {}", variant, AWS_S3);
                    return AWS_S3;
                });
    }
}

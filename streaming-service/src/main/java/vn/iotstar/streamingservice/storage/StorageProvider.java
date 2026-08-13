package vn.iotstar.streamingservice.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/** Bản sao độc lập của media-service's {@code StorageProvider} — tên enum phải khớp {@code Enum.name()}. */
@Slf4j
public enum StorageProvider {
    AWS_S3, CLOUDFLARE_R2, BACKBLAZE_B2;

    public static StorageProvider fromName(String name) {
        return Arrays.stream(values())
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown storage provider name '{}', falling back to {}", name, AWS_S3);
                    return AWS_S3;
                });
    }
}

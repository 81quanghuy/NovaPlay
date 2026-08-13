package vn.iotstar.transcodingworker.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Bản sao độc lập của {@code media-service}'s {@code StorageProvider} — không dùng shared lib.
 * Giá trị enum ({@code AWS_S3}, {@code CLOUDFLARE_R2}, {@code BACKBLAZE_B2}) phải khớp tên với
 * bản gốc: {@code VideoSourceReadyEvent.storageProvider} mang {@code Enum.name()}, worker
 * {@code valueOf} lại chuỗi đó để chọn client.
 */
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

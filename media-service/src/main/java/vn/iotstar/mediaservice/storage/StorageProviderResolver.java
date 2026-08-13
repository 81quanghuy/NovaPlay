package vn.iotstar.mediaservice.storage;

import dev.openfeature.sdk.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Điểm DUY NHẤT trong media-service hỏi OpenFeature xem storage provider nào đang active. Chỉ được
 * gọi cho UPLOAD MỚI ({@code MediaServiceImpl.requestUploadUrl}) — KHÔNG BAO GIỜ gọi cho một
 * {@link vn.iotstar.mediaservice.entity.Media} đã tồn tại. Thao tác trên record cũ (xoá, kiểm tra
 * tồn tại, sinh CDN URL) phải dùng {@code media.getEffectiveStorageProvider()}: nếu cờ đổi sau khi
 * một object đã nằm trên provider A, các thao tác sau đó với record đó vẫn phải trỏ đúng provider A,
 * không phải provider hiện tại của cờ — nếu không, xoá/kiểm tra object sẽ nhìn nhầm bucket.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageProviderResolver {

    private static final String FLAG_KEY = "media-storage-provider";

    private final Client openFeatureClient;

    @Value("${feature-flags.default-storage-provider:aws-s3}")
    private String defaultVariant;

    public StorageProvider resolveForNewUpload() {
        String variant = openFeatureClient.getStringValue(FLAG_KEY, defaultVariant);
        return StorageProvider.fromFlagVariant(variant);
    }
}

package vn.iotstar.streamingservice.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Đường phát segment HLS. Đây là cờ SỐNG, KHÔNG đóng băng vào bất kỳ record nào — khác hẳn
 * {@code Media.storageProvider} bên media-service (đóng băng vì file đã nằm cố định ở một bucket).
 * Delivery mode chỉ là quyết định sinh URL lúc serve: cùng một object phát được bằng cả hai đường,
 * nên được hỏi lại mỗi lần build rendition playlist.
 */
@Slf4j
public enum DeliveryMode {

    /** Presigned S3 GET URL trỏ thẳng origin — hành vi gốc, cũng là fallback an toàn. */
    DIRECT("direct"),

    /** URL sạch qua CDN, không query string nên cache được ở edge. */
    CLOUDFRONT("cloudfront");

    /** Key của document cờ trong MongoDB của config-service. */
    public static final String FLAG_KEY = "streaming-delivery-mode";

    private final String flagVariant;

    DeliveryMode(String flagVariant) {
        this.flagVariant = flagVariant;
    }

    public String flagVariant() {
        return flagVariant;
    }

    /**
     * Cờ cấu hình sai KHÔNG được làm hỏng playback — variant lạ rơi về {@link #DIRECT}, tệ nhất là
     * mất lợi ích CDN. Cùng tinh thần với {@code StorageProvider.fromFlagVariant} bên media-service.
     */
    public static DeliveryMode fromFlagVariant(String variant) {
        for (DeliveryMode mode : values()) {
            if (mode.flagVariant.equalsIgnoreCase(variant)) {
                return mode;
            }
        }
        log.warn("Unknown delivery mode flag variant '{}', falling back to {}", variant, DIRECT);
        return DIRECT;
    }
}

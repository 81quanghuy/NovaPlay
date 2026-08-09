package vn.iotstar.promotionservice.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình cho cơ chế outbox. Mọi giá trị đều có mặc định an toàn nên service không bắt buộc
 * khai báo gì trong {@code application-*.yml}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novaplay.outbox")
public class OutboxProperties {

    /** Tên kênh Postgres NOTIFY. Phải khớp với tên trong trigger {@code outbox_notify_trg}. */
    private String channel = "outbox_new";

    /** Số lượt gửi tối đa trước khi chuyển row sang FAILED. */
    private int maxAttempts = 10;

    /** Số row tối đa mỗi lô của một vòng catch-up. */
    private int batchSize = 100;

    /**
     * Thời gian chặn tối đa mỗi lần chờ notification. Hết thời gian này mà không có gì thì vòng
     * lặp thức dậy để kiểm tra cờ shutdown — không phát câu SQL nào.
     */
    private Duration listenTimeout = Duration.ofSeconds(30);

    /** Khoảng chờ trước lần kết nối lại đầu tiên khi mất connection LISTEN. */
    private Duration reconnectInitialDelay = Duration.ofSeconds(1);

    /** Trần của khoảng chờ kết nối lại. */
    private Duration reconnectMaxDelay = Duration.ofSeconds(30);

    /**
     * Bật gauge {@code outbox.pending}. Gauge này chạy một câu {@code count(*)} mỗi lần
     * Prometheus scrape (mặc định 15 giây), quét trên partial index nên rất rẻ. Đây là thứ duy
     * nhất phát hiện được row kẹt sau khi pod chết giữa lúc chờ thử lại — tắt đi thì mất luôn
     * khả năng cảnh báo đó.
     */
    private boolean pendingGaugeEnabled = true;
}

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

    /**
     * Bật cơ chế LISTEN/NOTIFY. Tắt khi Postgres không hỗ trợ nó — điển hình là các endpoint đi
     * qua PgBouncer ở chế độ transaction pooling (Neon pooled, Supabase pooler cổng 6543,
     * RDS Proxy): ở đó {@code LISTEN} hoặc bị từ chối, hoặc "thành công" rồi im lặng không bao
     * giờ nhận được notification nào — trường hợp thứ hai nguy hiểm hơn vì không có lỗi nào để
     * thấy.
     * <p>
     * Tắt LISTEN thì {@link #pollInterval} trở thành cơ chế duy nhất đẩy outbox đi, nên nhớ hạ
     * nó xuống mức chấp nhận được cho độ trễ gửi email.
     */
    private boolean listenEnabled = true;

    /**
     * Chu kỳ quét bảng outbox tìm row quá hạn, độc lập với LISTEN/NOTIFY.
     * <p>
     * Kể cả khi LISTEN bật, đây vẫn là lưới an toàn cần thiết: notification của Postgres KHÔNG
     * bền vững — row được INSERT trong lúc connection LISTEN đang đứt là mất tín hiệu vĩnh viễn,
     * và nếu pod chết sau COMMIT nhưng trước khi Kafka ack thì cũng không ai nhớ tới row đó nữa.
     * Trước đây chỗ này chỉ được xử lý bởi catch-up lúc khởi động/reconnect, nghĩa là một row có
     * thể nằm im vô thời hạn nếu connection không bao giờ đứt lại.
     * <p>
     * An toàn với nhiều pod mà không cần lock: {@code OutboxDao#claimBatch} nhận việc bằng
     * {@code FOR UPDATE SKIP LOCKED}, hai pod quét đồng thời sẽ chia nhau row chứ không gửi trùng.
     * <p>
     * Đặt {@code 0} để tắt hẳn.
     */
    private Duration pollInterval = Duration.ofSeconds(30);

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

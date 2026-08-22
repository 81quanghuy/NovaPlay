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

    /**
     * Đánh thức relay NGAY sau khi transaction nghiệp vụ COMMIT, trong cùng JVM. Đây là ĐƯỜNG
     * CHÍNH: thằng ghi row và thằng relay vốn nằm cùng một process, nên không cần đi vòng qua
     * Postgres NOTIFY (hay bất kỳ message broker nào) chỉ để tự đánh thức chính mình.
     * Tắt đi thì event chỉ được gửi khi sweep chạy, tức trễ tới trọn một chu kỳ sweep.
     */
    private boolean relayAfterCommit = true;

    /**
     * Sweep định kỳ nhặt row mồ côi — row đã COMMIT nhưng pod chết trước khi kịp relay, hoặc row
     * do một pod khác ghi rồi chết. Đây là LƯỚI AN TOÀN, không phải đường chính, nên chu kỳ để
     * thưa được. Tắt đi nghĩa là một pod chết đúng thời điểm sẽ làm event kẹt lại vĩnh viễn.
     */
    private boolean sweepEnabled = true;

    /** Chu kỳ sweep. Lượt đầu chạy ngay lúc khởi động để nhặt backlog mà pod trước bỏ lại. */
    private Duration sweepInterval = Duration.ofSeconds(60);

    /**
     * LISTEN/NOTIFY của Postgres. MẶC ĐỊNH TẮT, chỉ bật khi connection đi THẲNG tới Postgres.
     * <p>
     * Qua connection pooler (Supavisor của Supabase, PgBouncer) thì không dùng được: pooler chèn
     * message {@code ParameterStatus} vào connection đang chờ notification, mà pgjdbc chỉ xử lý
     * {@code 'A'/'E'/'N'} ở nhánh đó nên coi connection là hỏng — biểu hiện là
     * {@code "Unknown Response Type S"} lặp vô hạn. Kể cả khi chạy được, nó cũng chỉ là đường
     * vòng qua DB để đánh thức chính process này, nên {@code relayAfterCommit} luôn nhanh hơn.
     */
    private boolean listenEnabled = false;
}

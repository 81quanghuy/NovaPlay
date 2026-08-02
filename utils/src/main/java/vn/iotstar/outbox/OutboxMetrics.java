package vn.iotstar.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Ba counter và một gauge, đủ để dựng cảnh báo Grafana cho outbox.
 * <p>
 * Gauge {@code outbox.pending} là thứ duy nhất phát hiện được row kẹt trong tình huống pod chết
 * giữa lúc chờ thử lại (xem mục 6 của spec). Nó chạy một {@code count(*)} mỗi lần Prometheus
 * scrape; đây là chi phí có chủ ý, tắt được qua {@code novaplay.outbox.pending-gauge-enabled}.
 */
public class OutboxMetrics {

    private final Counter published;
    private final Counter relayFailed;
    private final Counter terminallyFailed;

    public OutboxMetrics(MeterRegistry registry, OutboxDao dao, OutboxProperties properties) {
        this.published = Counter.builder("outbox.published")
                .description("Số sự kiện outbox đã gửi thành công sang Kafka")
                .register(registry);
        this.relayFailed = Counter.builder("outbox.relay.failed")
                .description("Số lượt gửi thất bại, đã tính cả lượt sẽ được thử lại")
                .register(registry);
        this.terminallyFailed = Counter.builder("outbox.failed")
                .description("Số sự kiện đã hết lượt thử và chuyển sang FAILED")
                .register(registry);

        if (properties.isPendingGaugeEnabled()) {
            io.micrometer.core.instrument.Gauge
                    .builder("outbox.pending", dao, OutboxDao::countPending)
                    .description("Số row outbox đang chờ gửi. Lớn hơn 0 kéo dài nghĩa là có row kẹt")
                    .register(registry);
        }
    }

    public void published() {
        published.increment();
    }

    public void relayFailed() {
        relayFailed.increment();
    }

    public void terminallyFailed() {
        terminallyFailed.increment();
    }
}

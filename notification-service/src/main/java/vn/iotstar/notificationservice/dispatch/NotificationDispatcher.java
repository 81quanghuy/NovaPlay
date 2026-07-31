package vn.iotstar.notificationservice.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.channel.NotificationChannel;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.ChannelType;
import vn.iotstar.notificationservice.service.DedupStore;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phát một {@link NotificationRequest} tới mọi kênh mà nó yêu cầu, với cách ly lỗi theo từng kênh.
 * <p>
 * Kafka giao tin theo ngữ nghĩa at-least-once: nếu bản ghi bị ném lại (do một kênh lỗi), toàn bộ
 * listener sẽ chạy lại từ đầu. Không có gì ở đây ngăn dispatch() được gọi nhiều lần cho cùng một
 * request — cái ngăn gửi trùng là khoá chống trùng theo <em>từng kênh</em> bên dưới, không phải
 * việc gọi dispatch() bao nhiêu lần.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final Map<ChannelType, NotificationChannel> channelsByType;
    private final DedupStore dedupStore;
    private final NotificationMetrics metrics;

    public NotificationDispatcher(List<NotificationChannel> channels, DedupStore dedupStore,
                                   NotificationMetrics metrics) {
        this.channelsByType = new EnumMap<>(ChannelType.class);
        channels.forEach(c -> channelsByType.put(c.type(), c));
        this.dedupStore = dedupStore;
        this.metrics = metrics;
    }

    /**
     * @throws ChannelDeliveryException nếu ít nhất một kênh thất bại — retryable, Kafka sẽ giao
     *                                   lại bản ghi. Các kênh đã thành công đã tự khoá chống trùng
     *                                   nên lượt sau chỉ chạm tới đúng kênh còn hỏng.
     */
    public void dispatch(NotificationRequest request) {
        Map<ChannelType, RuntimeException> failures = new EnumMap<>(ChannelType.class);

        for (ChannelType type : request.channels()) {
            NotificationChannel channel = channelsByType.get(type);
            if (channel == null) {
                // Kênh được yêu cầu nhưng chưa có impl (vd. WEBSOCKET, PUSH ở phase này). Đây không
                // phải lỗi tạm thời — retry không giúp ích — nên bỏ qua thay vì góp vào failures.
                log.warn("Bỏ qua kênh chưa hỗ trợ: {} cho dedupKey={}", type, request.dedupKey());
                continue;
            }

            String dedupKey = request.dedupKey() + ":" + type;
            if (!dedupStore.acquireOnce(dedupKey, DEDUP_TTL)) {
                log.info("Bỏ qua trùng: kênh={} dedupKey={}", type, request.dedupKey());
                metrics.channelSkipped(type);
                continue;
            }

            try {
                channel.send(request);
                metrics.channelSent(type);
            } catch (RuntimeException ex) {
                metrics.channelFailed(type);
                if (channel.isRetryable(ex)) {
                    dedupStore.release(dedupKey);
                }
                log.error("Gửi thất bại: kênh={} dedupKey={}", type, request.dedupKey(), ex);
                failures.put(type, ex);
                // Cố tình không return/throw ở đây — các kênh còn lại phải được thử hết trước khi
                // quyết định có ném ChannelDeliveryException hay không.
            }
        }

        if (!failures.isEmpty()) {
            throw new ChannelDeliveryException(failures);
        }
    }
}

package vn.iotstar.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Nhật ký kiểm toán có cấu trúc cho việc xử lý sự kiện và gửi thông báo.
 * <p>
 * Ghi theo cặp key=value để Loki truy vấn được, kèm traceId lấy từ MDC nhằm nối một dòng log
 * với toàn bộ trace phân tán tương ứng.
 */
@Component
@Slf4j
public class AuditLogger {

    public void eventReceived(String topic, String dedupKey) {
        log.info("event=notification.event.received topic={} dedupKey={} traceId={}",
                topic, dedupKey, traceId());
    }

    public void deadLettered(String topic, String key) {
        log.error("event=notification.event.dead-lettered topic={} key={} traceId={}",
                topic, key, traceId());
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "none" : traceId;
    }
}

package vn.iotstar.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Nhật ký kiểm toán có cấu trúc cho các hành động thay đổi dữ liệu người dùng.
 * <p>
 * Ghi theo cặp key=value để Loki truy vấn được, kèm traceId lấy từ MDC nhằm nối một dòng log
 * với toàn bộ trace phân tán tương ứng.
 */
@Component
@Slf4j
public class AuditLogger {

    public void profileUpdated(String email) {
        log.info("event=user.profile.updated email={} traceId={}", email, traceId());
    }

    public void avatarChanged(String userId, String url) {
        log.info("event=user.avatar.changed userId={} url={} traceId={}", userId, url, traceId());
    }

    public void favoriteAdded(String email, String movieId) {
        log.info("event=user.favorite.added email={} movieId={} traceId={}", email, movieId, traceId());
    }

    public void favoritesCleared(String email, long removed) {
        log.info("event=user.favorites.cleared email={} removed={} traceId={}", email, removed, traceId());
    }

    public void profileRegistered(String email) {
        log.info("event=user.profile.registered email={} traceId={}", email, traceId());
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "none" : traceId;
    }
}

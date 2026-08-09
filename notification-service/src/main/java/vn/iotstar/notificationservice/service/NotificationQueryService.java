package vn.iotstar.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.notificationservice.model.dto.NotificationDTO;

/**
 * Đọc và cập nhật trạng thái đọc của thông báo in-app.
 * <p>
 * Mọi phương thức nhận {@code userEmail} tường minh thay vì đọc lại từ
 * {@code SecurityContextHolder}: controller là nơi duy nhất được phép biết principal hiện tại là
 * ai, service không nên tự đi lấy lại vì điều đó khiến việc audit "hàm này lọc theo ai" khó hơn.
 */
public interface NotificationQueryService {

    Page<NotificationDTO> list(String userEmail, Pageable pageable, boolean unreadOnly);

    long unreadCount(String userEmail);

    /**
     * @throws vn.iotstar.notificationservice.exception.ResourceNotFoundException nếu không có thông
     *         báo nào khớp cả {@code id} lẫn {@code userEmail} — cố tình trả 404 thay vì 403 để
     *         không tiết lộ rằng thông báo đó tồn tại và thuộc về người khác.
     */
    NotificationDTO markRead(String userEmail, String id);

    /** @return số thông báo vừa được đánh dấu đã đọc. */
    long markAllRead(String userEmail);
}

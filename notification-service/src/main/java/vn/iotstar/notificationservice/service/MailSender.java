package vn.iotstar.notificationservice.service;

import vn.iotstar.notificationservice.model.dto.NotificationRequest;

/**
 * Biết cách dựng và gửi đúng một email cho một {@link NotificationRequest}.
 * <p>
 * Tách khỏi {@code NotificationChannel} để logic render template + SMTP kiểm thử được độc lập,
 * không phụ thuộc vào khoá chống trùng hay vòng lặp fan-out của dispatcher.
 */
public interface MailSender {

    /**
     * @throws MailDeliveryException nếu không dựng được nội dung hoặc SMTP từ chối email. Mọi
     *                                trường hợp ném ra từ đây đều xảy ra trước khi máy chủ SMTP
     *                                nhận thư, nên an toàn để gọi lại.
     */
    void send(NotificationRequest request);
}

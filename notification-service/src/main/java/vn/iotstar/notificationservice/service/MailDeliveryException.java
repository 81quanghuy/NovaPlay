package vn.iotstar.notificationservice.service;

/** Không dựng được nội dung email, hoặc SMTP từ chối trước khi nhận thư — luôn an toàn để thử lại. */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

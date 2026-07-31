package vn.iotstar.notificationservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.iotstar.utils.constants.GenericResponse;

import java.time.Instant;

/**
 * Xử lý các exception hạ tầng mà {@code GlobalExceptionHandler} dùng chung không biết tới.
 * <p>
 * Chạy ở mức ưu tiên cao nhất để thắng advice trong {@code utils} — nếu không, catch-all bên đó
 * sẽ biến tất cả những lỗi dưới đây thành 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class NotificationServiceExceptionHandler {

    private static ResponseEntity<GenericResponse> error(HttpStatusCode status, String message,
                                                          HttpServletRequest request) {
        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(message)
                        .statusCode(status.value())
                        .timestamp(Instant.now())
                        .path(request == null ? null : request.getRequestURI())
                        .build(), status);
    }

    /** Có thể xảy ra nếu hai request markRead chạm cùng lúc trên một document mới tạo. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<GenericResponse> handleDuplicateKey(DuplicateKeyException e,
                                                               HttpServletRequest request) {
        log.warn("Duplicate key violation: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    /** Mất kết nối MongoDB/Redis. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<GenericResponse> handleDataStoreUnavailable(DataAccessResourceFailureException e,
                                                                       HttpServletRequest request) {
        log.error("Data store is unreachable", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable, please try again later", request);
    }
}

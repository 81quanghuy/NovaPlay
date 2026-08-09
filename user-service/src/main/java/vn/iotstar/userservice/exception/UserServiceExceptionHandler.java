package vn.iotstar.userservice.exception;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.iotstar.userservice.common.GenericResponse;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Xử lý các exception hạ tầng mà {@code GlobalExceptionHandler} dùng chung không biết tới.
 * <p>
 * Chạy ở mức ưu tiên cao nhất để thắng advice trong {@code utils} — nếu không, catch-all
 * bên đó sẽ biến tất cả những lỗi dưới đây thành 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class UserServiceExceptionHandler {

    private static ResponseEntity<GenericResponse> error(HttpStatusCode status,
                                                         String message,
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

    /** Vi phạm unique index — chẳng hạn thêm trùng một phim vào danh sách yêu thích. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<GenericResponse> handleDuplicateKey(DuplicateKeyException e,
                                                              HttpServletRequest request) {
        log.warn("Duplicate key violation: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    /** Hai lần ghi đồng thời lên cùng một document; client nên đọc lại rồi thử lại. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<GenericResponse> handleOptimisticLocking(OptimisticLockingFailureException e,
                                                                   HttpServletRequest request) {
        log.warn("Optimistic locking failure: {}", e.getMessage());
        return error(HttpStatus.CONFLICT,
                "The resource was modified concurrently, please retry", request);
    }

    /** Circuit breaker đang mở: service phụ thuộc đang hỏng, không gọi tới nữa. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<GenericResponse> handleCircuitOpen(CallNotPermittedException e,
                                                             HttpServletRequest request) {
        log.warn("Circuit breaker '{}' is open, rejecting call", e.getCausingCircuitBreakerName());
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "A downstream service is temporarily unavailable, please try again later", request);
    }

    /**
     * Lời gọi Feign thất bại. Lỗi phía client (4xx) được chuyển tiếp nguyên trạng vì
     * nguyên nhân nằm ở request; lỗi phía server thành 502 vì lỗi thuộc về upstream.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<GenericResponse> handleFeign(FeignException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.status());
        if (status != null && status.is4xxClientError()) {
            log.warn("Downstream service rejected the request with {}: {}", e.status(), e.getMessage());
            return error(status, "The request was rejected by a downstream service", request);
        }
        log.error("Downstream service call failed with status {}", e.status(), e);
        return error(HttpStatus.BAD_GATEWAY, "A downstream service failed to respond", request);
    }

    /** Mất kết nối MongoDB/Redis — cơ sở dữ liệu không tới được. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<GenericResponse> handleDataStoreUnavailable(DataAccessResourceFailureException e,
                                                                      HttpServletRequest request) {
        log.error("Data store is unreachable", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable, please try again later", request);
    }

    /** Validation ở tầng tham số (@RequestParam/@PathVariable) trên controller có @Validated. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                     HttpServletRequest request) {
        String details = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", details);
        return error(HttpStatus.BAD_REQUEST, details, request);
    }
}

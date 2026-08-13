package vn.iotstar.streamingservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.streamingservice.common.GenericResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    private static ResponseEntity<GenericResponse> error(HttpStatusCode status, String message) {
        return error(status, message, null, null);
    }

    private static ResponseEntity<GenericResponse> error(HttpStatusCode status,
                                                         String message,
                                                         Object result,
                                                         HttpServletRequest request) {
        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(message)
                        .result(result)
                        .statusCode(status.value())
                        .timestamp(Instant.now())
                        .path(request == null ? null : request.getRequestURI())
                        .build(), status);
    }

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    public ResponseEntity<GenericResponse> handleValidationException(final BindException e) {
        log.warn("Validation error occurred: {}", e.getMessage());
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });
        return error(HttpStatus.BAD_REQUEST, "Validation failed for request data", errors, null);
    }

    @ExceptionHandler(value = {HttpMessageNotReadableException.class})
    public ResponseEntity<GenericResponse> handleUnreadableMessage(final HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body");
    }

    @ExceptionHandler(value = {ResponseStatusException.class})
    public ResponseEntity<GenericResponse> handleResponseStatusException(final ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String reason = e.getReason() != null ? e.getReason() : status.toString();
        log.warn("Request failed with status {}: {}", status.value(), reason);
        return error(status, reason);
    }

    @ExceptionHandler(value = {BadRequestException.class})
    public ResponseEntity<GenericResponse> handleBadRequestException(final BadRequestException e) {
        log.warn("Bad request received: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(value = {ResourceNotFoundException.class})
    public ResponseEntity<GenericResponse> handleNotFoundException(final ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(value = {ForbiddenException.class})
    public ResponseEntity<GenericResponse> handleForbiddenException(final ForbiddenException e) {
        log.warn("Forbidden access attempt: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(value = {UnauthorizedPlaybackException.class})
    public ResponseEntity<GenericResponse> handleUnauthorizedPlayback(final UnauthorizedPlaybackException e) {
        log.warn("Unauthorized playback attempt: {}", e.getMessage());
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * Feign call sang movie-service/media-service trả lỗi (404 phim không tồn tại, 404 manifest
     * chưa có, ...). Truyền nguyên status code gốc ra client thay vì luôn quy về 500 — caller
     * (frontend) cần phân biệt "phim không tồn tại" với "streaming-service tự lỗi".
     */
    @ExceptionHandler(value = {feign.FeignException.class})
    public ResponseEntity<GenericResponse> handleFeignException(final feign.FeignException e) {
        HttpStatus status = HttpStatus.resolve(e.status());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        log.warn("Upstream service call failed with status {}: {}", e.status(), e.getMessage());
        return error(status, "Upstream service call failed (" + status.value() + ")");
    }

    @ExceptionHandler(value = {org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<GenericResponse> handleAccessDeniedException(
            final org.springframework.security.access.AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Catch-all. Chỉ đăng ký Exception.class để không nuốt các handler mặc định của Spring MVC (404/405/415). */
    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<GenericResponse> handleServerException(final Exception e,
                                                                 final HttpServletRequest request) {
        log.error("An unexpected error occurred", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, null, request);
    }
}

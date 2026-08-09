package vn.iotstar.promotionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.promotionservice.common.GenericResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /**
     * Message trả cho client trên lỗi 500. Message thật của exception chỉ nằm trong log —
     * echo ra ngoài sẽ lộ chi tiết nội bộ (lỗi driver DB, message NPE, ...).
     */
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

    /**
     * Body JSON không đọc được. Tách riêng khỏi {@link #handleValidationException} vì
     * HttpMessageNotReadableException KHÔNG phải BindException — gộp chung gây ClassCastException.
     * Không echo message gốc vì nó lộ tên field và cấu trúc class nội bộ.
     */
    @ExceptionHandler(value = {HttpMessageNotReadableException.class})
    public ResponseEntity<GenericResponse> handleUnreadableMessage(final HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body");
    }

    /**
     * Giữ đúng status và lý do mà caller đã chỉ định. Không có handler này thì
     * ResponseStatusException rơi xuống catch-all và mọi 404/409 biến thành 500.
     */
    @ExceptionHandler(value = {ResponseStatusException.class})
    public ResponseEntity<GenericResponse> handleResponseStatusException(final ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        // getReason() là message thuần; getMessage() có kèm prefix kiểu `404 NOT_FOUND "..."`.
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

    @ExceptionHandler(value = {UnsupportedMediaTypeException.class})
    public ResponseEntity<GenericResponse> handleUnsupportedMediaTypeException(final UnsupportedMediaTypeException e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(value = {ForbiddenException.class})
    public ResponseEntity<GenericResponse> handleForbiddenException(final ForbiddenException e) {
        log.warn("Forbidden access attempt: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(value = {org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<GenericResponse> handleAccessDeniedException(
            final org.springframework.security.access.AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(value = {MissingRequestHeaderException.class})
    public ResponseEntity<GenericResponse> handleMissingRequestHeaderException(final MissingRequestHeaderException e) {
        log.warn("Unauthorized access attempt due to missing header: {}", e.getHeaderName());
        return error(HttpStatus.UNAUTHORIZED,
                "Required request header '" + e.getHeaderName() + "' is not present");
    }

    @ExceptionHandler(value = {UserAlreadyExistsException.class})
    public ResponseEntity<GenericResponse> handleUserAlreadyExistsException(final UserAlreadyExistsException e) {
        log.warn("A user creation attempt failed due to a conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(value = {TooManyRequestsException.class})
    public ResponseEntity<GenericResponse> handleTooManyRequestsException(final TooManyRequestsException e) {
        log.warn("Rate limit exceeded: {}", e.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    /**
     * Catch-all. Chỉ đăng ký Exception.class: thêm RuntimeException.class sẽ nuốt cả những
     * exception đã có handler chuyên biệt của Spring MVC (404/405/415 mặc định).
     */
    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<GenericResponse> handleServerException(final Exception e,
                                                                 final HttpServletRequest request) {
        log.error("An unexpected error occurred", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, null, request);
    }
}

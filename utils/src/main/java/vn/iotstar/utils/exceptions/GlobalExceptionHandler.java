package vn.iotstar.utils.exceptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.exceptions.wrapper.*;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(value = {
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<GenericResponse> handleValidationException(final BindException e) {
        log.warn("Validation error occurred: {}", e.getMessage());
        final var badRequest = HttpStatus.BAD_REQUEST;
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
        });

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message("Validation failed for request data")
                        .result(errors)
                        .statusCode(badRequest.value())
                        .build(), badRequest);
    }

    @ExceptionHandler(value = {BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<GenericResponse> handleBadRequestException(final RuntimeException e) {
        log.warn("Bad request received: {}", e.getMessage());
        final var badRequest = HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(badRequest.value())
                        .build(), badRequest);
    }

    @ExceptionHandler(value = {ResourceNotFoundException.class})
    public ResponseEntity<GenericResponse> handleNotFoundException(final ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        final var notFound = HttpStatus.NOT_FOUND;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(notFound.value())
                        .build(), notFound);
    }

    @ExceptionHandler(value = {UnsupportedMediaTypeException.class})
    public <T extends RuntimeException> ResponseEntity<GenericResponse> handleUnsupportedMediaTypeException(final T e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        final var unsupportedMediaType = HttpStatus.UNSUPPORTED_MEDIA_TYPE;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(unsupportedMediaType.value())
                        .build(), unsupportedMediaType);
    }

    @ExceptionHandler(value = {ForbiddenException.class})
    public ResponseEntity<GenericResponse> handleForbiddenException(final ForbiddenException e) {
        log.warn("Forbidden access attempt: {}", e.getMessage());
        final var forbidden = HttpStatus.FORBIDDEN;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(forbidden.value())
                        .build(), forbidden);
    }

    @ExceptionHandler(value = {
            NullPointerException.class,
            Exception.class,
            RuntimeException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<GenericResponse> handleServerException(final RuntimeException e) {
        log.warn("An unexpected error occurred: {}", e.getMessage());
        final var internalServerError = HttpStatus.INTERNAL_SERVER_ERROR;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(internalServerError.value())
                        .build(), internalServerError);
    }

    @ExceptionHandler(value = {MissingRequestHeaderException.class})
    public ResponseEntity<GenericResponse> handleMissingRequestHeaderException(final MissingRequestHeaderException e) {
        log.warn("Unauthorized access attempt due to missing header: {}", e.getHeaderName());
        final var unauthorized = HttpStatus.UNAUTHORIZED;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message("Required request header '" + e.getHeaderName() + "' is not present")
                        .statusCode(unauthorized.value())
                        .build(), unauthorized);
    }

    @ExceptionHandler(value = {UserAlreadyExistsException.class})
    public ResponseEntity<GenericResponse> handleUserAlreadyExistsException(final UserAlreadyExistsException e) {
        log.warn("A user creation attempt failed due to a conflict: {}", e.getMessage());
        final var conflict = HttpStatus.CONFLICT;

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .statusCode(conflict.value())
                        .build(), conflict);
    }

    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<GenericResponse> handleServerException(final Exception e) {
        log.error("An unexpected server error occurred: {}", e.getMessage(), e);
        final var internalServerError = HttpStatus.INTERNAL_SERVER_ERROR;

        String message = "An internal server error occurred. Please try again later.";

        return new ResponseEntity<>(
                GenericResponse.builder()
                        .success(false)
                        .message(message)
                        .statusCode(internalServerError.value())
                        .build(), internalServerError);
    }
}

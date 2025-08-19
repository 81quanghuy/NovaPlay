package vn.iotstar.utils.exceptions.wrapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user already exists in the system.
 * This exception is typically used to indicate that an attempt to create a user
 * with an email or username that is already registered has failed.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
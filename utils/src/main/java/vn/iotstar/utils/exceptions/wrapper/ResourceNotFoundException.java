package vn.iotstar.utils.exceptions.wrapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static vn.iotstar.utils.constants.AppConst.MESSAGE_NOT_FOUND;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message, String fieldName, String fieldValue) {
        super(String.format(MESSAGE_NOT_FOUND, message, fieldName, fieldValue));
    }
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

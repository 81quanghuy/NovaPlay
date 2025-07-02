package vn.iotstar.utils.exceptions.wrapper;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

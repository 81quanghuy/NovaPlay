package vn.iotstar.utils.exceptions.wrapper;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}

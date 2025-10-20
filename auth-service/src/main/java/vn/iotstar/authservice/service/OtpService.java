package vn.iotstar.authservice.service;

public interface OtpService {
    void generateAndDispatch(String userId, String email, String locale, String correlationId);

    boolean verify(String userId, String inputOtp);
}

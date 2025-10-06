package vn.iotstar.authservice.service;

public interface IRecaptchaService {
    /**
     * Validates the reCAPTCHA response.
     *
     * @param captchaResponse the response from reCAPTCHA
     * @param clientIp the IP address of the client making the request
     * @return true if the reCAPTCHA is valid, false otherwise
     */
    boolean validateCaptcha(String captchaResponse, String clientIp);
}

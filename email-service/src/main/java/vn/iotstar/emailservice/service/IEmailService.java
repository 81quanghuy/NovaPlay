package vn.iotstar.emailservice.service;


public interface IEmailService {

    /**
     * Sends an email with the specified parameters.
     *
     * @param email      The recipient's email address.
     * @param otp        The subject of the email.
     * @param expire     The body content of the email.
     * @param locale     The locale for the email content.
     */
    void sendOTP(String email, String otp, String expire, String locale);
}

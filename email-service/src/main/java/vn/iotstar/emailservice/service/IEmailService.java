package vn.iotstar.emailservice.service;


import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.EmailRequest;

import java.io.UnsupportedEncodingException;

public interface IEmailService {

    /**
     * Sends an OTP to the specified email address.
     *
     * @param emailRequest the request containing the email address and other details
     * @return a ResponseEntity containing a GenericResponse with the result of the operation
     */
    ResponseEntity<GenericResponse> sendOTP(EmailRequest emailRequest)
            throws MessagingException, UnsupportedEncodingException;
    /**
     * Verifies the OTP for the given email address.
     *
     * @param emailDTO the DTO containing the email address, OTP, and expiration time
     * @return a ResponseEntity containing a GenericResponse with the result of the verification
     */
    ResponseEntity<GenericResponse> verifyOTP(@Valid EmailDTO emailDTO);
}

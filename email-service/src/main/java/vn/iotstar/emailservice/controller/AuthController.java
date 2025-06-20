package vn.iotstar.emailservice.controller;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.service.IEmailService;

import java.io.UnsupportedEncodingException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IEmailService emailService;

    /**
     * Endpoint to send OTP for user registration
     *
     * @param pRegisterRequest contains user registration details
     * @return ResponseEntity with GenericResponse containing OTP information
     * @throws MessagingException if there is an error sending the email
     * @throws UnsupportedEncodingException if there is an error with character encoding
     */
    @PostMapping("/send-otp")
    public ResponseEntity<GenericResponse> sendOTP(@Valid @RequestBody EmailDTO pRegisterRequest)
            throws MessagingException, UnsupportedEncodingException {
        log.info("Send OTP for email register: {}", pRegisterRequest.getEmail());
        return emailService.sendOTP(pRegisterRequest);
    }
}

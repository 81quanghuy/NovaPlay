package vn.iotstar.emailservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.utils.dto.EmailRequest;

import java.io.UnsupportedEncodingException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/emails")
public class EmailController {

    private final IEmailService emailService;

    @Operation(
            summary = "Verify OTP for user registration",
            description = "This endpoint verifies the One-Time Password (OTP) sent to the user's email during registration."
    )
    @PostMapping("/verify-otp")
    public ResponseEntity<GenericResponse> verifyOTP(@Valid @RequestBody EmailDTO emailDTO) {
        log.info("Verify OTP for email: {}", emailDTO.email());
        return emailService.verifyOTP(emailDTO);
    }

    @PostMapping("/resend-otp")
    @Operation(
            summary = "Resend OTP for user registration",
            description = "This endpoint resends a One-Time Password (OTP) to the user's email for registration purposes."
    )
    public ResponseEntity<GenericResponse> resendOTP(@Valid @RequestBody EmailRequest emailRequest)
            throws MessagingException, UnsupportedEncodingException {
        log.info("Resend OTP for email: {}", emailRequest.recipientEmail());
        return emailService.sendOTP(emailRequest);
    }
}

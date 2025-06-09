package vn.iotstar.authservice.controller;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.service.impl.AccountService;

import java.io.UnsupportedEncodingException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;

    /**
     * Login method for user authentication
     *
     * @param accountDTO contains username and password
     * @return ResponseEntity with GenericResponse containing login information
     */
    @GetMapping("/login")
    public ResponseEntity<GenericResponse> login(@Valid @RequestBody AccountDTO accountDTO) throws Exception {
        log.info("Login request received for user: {}", accountDTO.getEmail());
        return accountService.login(accountDTO);
    }

    /**
     * Endpoint to send OTP for user registration
     *
     * @param pRegisterRequest contains user registration details
     * @return ResponseEntity with GenericResponse containing OTP information
     * @throws MessagingException if there is an error sending the email
     * @throws UnsupportedEncodingException if there is an error with character encoding
     */
    @PostMapping("/send-otp")
    public ResponseEntity<GenericResponse> sendOTP(@Valid @RequestBody AccountDTO pRegisterRequest)
            throws MessagingException, UnsupportedEncodingException {
        log.info("Send OTP for email register: {}", pRegisterRequest.getEmail());
        return accountService.sendOTP(pRegisterRequest);
    }

    // Register endpoint for user registration
    @PostMapping("/register")
    public ResponseEntity<GenericResponse> register(@Valid @RequestBody AccountDTO accountDTO) {
        log.info("Register request received for user: {}", accountDTO.getEmail());
        return accountService.register(accountDTO);
    }
}

package vn.iotstar.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.service.IAccountService;

import java.io.UnsupportedEncodingException;

@Tag(
        name = "Authentication",
        description = "CRUD operations for user authentication and registration"
)
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IAccountService accountService;

    /**
     * Login method for user authentication
     *
     * @param accountDTO contains username and password
     * @return ResponseEntity with GenericResponse containing login information
     */
    @Operation(
            summary = "Login",
            description = "Authenticate user with email and password"
    )
    @PostMapping("/login")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(
                            schema = @Schema(implementation = GenericResponse.class),
                            mediaType = "application/json"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"

            ),
    })
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

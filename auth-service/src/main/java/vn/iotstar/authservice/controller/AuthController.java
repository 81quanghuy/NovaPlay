package vn.iotstar.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.service.impl.AccountService;

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
        log.info("Login request received for user: {}", accountDTO.getUsername());
        return accountService.login(accountDTO);
    }


    /**
     * Register method for user account creation
     *
     * @param registerRequest contains user registration details
     * @return ResponseEntity with GenericResponse containing registration information
     */
    @PostMapping("/register")
    public ResponseEntity<GenericResponse> registerProcess(@Valid @RequestBody AccountDTO registerRequest) {
        log.info("Register request received for user: {}", registerRequest.getUsername());
        return accountService.userRegister(registerRequest);
    }
}

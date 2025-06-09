package vn.iotstar.authservice.service.impl;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iostar.utils.constants.GenericResponse;
import vn.iostar.utils.jwt.service.JwtService;
import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.model.entity.Account;
import vn.iotstar.authservice.model.entity.Email;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.repository.AccountRepository;
import vn.iotstar.authservice.service.IAccountService;
import vn.iotstar.authservice.service.client.UserClient;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.authservice.util.TokenType;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.*;

import static vn.iotstar.authservice.util.MessageProperties.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private final AccountRepository accountRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailService emailService;
    private final Environment env;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
    public static final int OTP_LENGTH = 6; // Length of the OTP

    /**
     * Find account by email
     */
    private Account findByEmail(String pEmail) {
        return accountRepository.findByEmail(pEmail).orElse(null);
    }

    /**
     * Login method for account
     *
     * @param pAccountDTO Account data transfer object
     * @return ResponseEntity with GenericResponse containing login information
     */
    @Override
    public ResponseEntity<GenericResponse> login(AccountDTO pAccountDTO) throws Exception {
        log.info("AccountService, login, accountDTO: {}", pAccountDTO);

        Account account = findByEmail(pAccountDTO.getEmail());
        validateAccountLogin(account, pAccountDTO.getPassword());

        // Create refresh token
        Token refreshToken = Token.builder()
                .accountId(account.getId())
                .type(TokenType.REFRESH_TOKEN)
                .tokenValue(createRefreshToken(account))
                .issuedAt(new Date())
                .expiredAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
                .ipAddress(pAccountDTO.getIpAddress())
                .build();
        tokenService.save(refreshToken);

        Map<String, Object> claims = Map.of(
                "id", account.getId(),
                "email", account.getEmail(),
                "roles", account.getRoles().stream().map(Role::getRoleName).toList()
        );
        String accessToken = jwtService.generateAccessToken(claims, account.getUserId());

        Map<String, String> tokenMap = Map.of(
                "accessToken", accessToken,
                "accountId", String.valueOf(account.getId()),
                "email", account.getEmail(),
                "roles", account.getRoles().stream()
                        .map(Role::getRoleName)
                        .toList()
                        .toString()
        );

        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(LOGIN_SUCCESS)
                .result(tokenMap)
                .statusCode(HttpStatus.OK.value())
                .build());
    }

    /**
     * Register a new account and send OTP to email
     *
     * @param PRegisterRequest Account data transfer object for registration
     * @return ResponseEntity with GenericResponse containing registration information
     * @throws MessagingException if there is an error sending the email
     * @throws UnsupportedEncodingException if there is an error encoding the email content
     */
    @Override
    public ResponseEntity<GenericResponse> sendOTP(AccountDTO PRegisterRequest)
            throws MessagingException, UnsupportedEncodingException {
        log.info("AccountService, userRegister, registerRequest: {}", PRegisterRequest);

        // validate the registration request
        validateAccountRegister(PRegisterRequest);
        // send OTP to email
        sendOTPEmail(PRegisterRequest.getEmail());
        // create a new account
        Account newAccount = Account.builder()
                .email(PRegisterRequest.getEmail())
                .password(PRegisterRequest.getPassword())
                .isActive(false) // Set to false until email is verified
                .roles(Set.of(Role.builder().roleName(RoleName.ROLE_USER).build())) // Default role
                .build();
        accountRepository.save(newAccount);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(SEND_OTP_SUCCESS)
                .result(newAccount)
                .statusCode(HttpStatus.CREATED.value())
                .build());
    }

    // Send UserClientService to create user
    /**
     * Register a new user account
     *
     * @param registerRequest Account data transfer object for registration
     * @return ResponseEntity with GenericResponse containing registration information
     */
    @Override
    public ResponseEntity<GenericResponse> register(AccountDTO registerRequest) {
        return userClient.createUser(registerRequest);
    }

    /**
     * Validate the account registration request
     *
     * @param pRegisterRequest Account data transfer object for registration
     */
    private void validateAccountRegister(AccountDTO pRegisterRequest) {
        log.info("AccountService, validateAccountRegister, registerRequest: {}", pRegisterRequest);
        // Check if email already exists
        if (accountRepository.findByEmail(pRegisterRequest.getEmail()).isPresent()) {
            throw new RuntimeException(EMAIL_ALREADY_EXISTS);
        }
        // Check if password is valid
        // pwd must be at least 8 characters long, contain at least one uppercase letter, one lowercase letter
        // one digit, and one special character
        if (pRegisterRequest.getPassword() == null || pRegisterRequest.getPassword().length() < 8 ||
                !pRegisterRequest.getPassword().matches(".*[A-Z].*") ||
                !pRegisterRequest.getPassword().matches(".*[a-z].*") ||
                !pRegisterRequest.getPassword().matches(".*\\d.*") ||
                !pRegisterRequest.getPassword().matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            throw new RuntimeException(INVALID_PASSWORD);
        }

        // Hash the password before saving
        pRegisterRequest.setPassword(passwordEncoder.encode(pRegisterRequest.getPassword()));
    }

    /**
     * Create a JWT token for the account
     *
     * @param pAccount Account entity
     * @return JWT token as a String
     * @throws Exception if token generation fails
     */
    private String createRefreshToken(Account pAccount) throws Exception {
        Map<String, Object> claims = Map.of(
                "id", pAccount.getId(),
                "email", pAccount.getEmail(),
                "roles", pAccount.getRoles().stream().map(Role::getRoleName).toList()
        );
        return jwtService.generateRefreshToken(claims, pAccount.getUserId());
    }

    /**
     * Validate account login credentials
     *
     * @param pAccount  Account entity
     * @param pPassword password to validate
     */
    private void validateAccountLogin(Account pAccount, String pPassword) {
        // Check if account exists
        if (pAccount == null) {
            throw new RuntimeException(ACCOUNT_NOT_FOUND);
        }
        // Check if password matches
        if (!passwordEncoder.matches(pPassword, pAccount.getPassword())) {
            throw new RuntimeException(INVALID_PASSWORD);
        }
        // Check if account is active
        if (!Boolean.TRUE.equals(pAccount.getIsActive())) {
            throw new RuntimeException(ACCOUNT_INACTIVE);
        }

    }

    /**
     * Send OTP to the user's email
     *
     * @param pEmail User's email address
     */
    private void sendOTPEmail(String pEmail) throws MessagingException, UnsupportedEncodingException {
        String otp = generateOtp();
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(pEmail);

        // Load Thymeleaf template
        Context context = new Context();
        context.setVariable("otpCode", otp);
        context.setVariable("verifyEmail", pEmail);
        String mailContent = templateEngine.process("send-otp", context);

        helper.setText(mailContent, true);
        helper.setSubject("The verification token for NOVAPLAY");
        helper.setFrom(Objects.requireNonNull(env.getProperty("spring.mail.username")), "NovaPlay Team");
        mailSender.send(message);

        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(5);
        Email emailVerification = new Email();
        emailVerification.setEmail(pEmail);
        emailVerification.setOtp(otp);
        emailVerification.setExpirationTime(expirationTime);
        emailService.save(emailVerification);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}

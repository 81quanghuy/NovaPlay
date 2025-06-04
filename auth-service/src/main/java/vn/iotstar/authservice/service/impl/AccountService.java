package vn.iotstar.authservice.service.impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.iostar.utils.constants.GenericResponse;
import vn.iostar.utils.jwt.service.JwtService;
import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.model.entity.Account;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.repository.AccountRepository;
import vn.iotstar.authservice.service.IAccountService;
import vn.iotstar.authservice.util.TokenType;

import java.util.Date;
import java.util.Map;

import static vn.iotstar.authservice.util.MessageProperties.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {
    private final AccountRepository accountRepository;
    private final TokenService tokenService;
    private final JwtService jwtService ;
    private final PasswordEncoder passwordEncoder;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

    /**
     * Find account by username
     */
    private Account findByUsername(String username) {
        return accountRepository.findByUsername(username).orElse(null);
    }

    /**
     * Login method for account
     *
     * @param accountDTO Account data transfer object
     * @return ResponseEntity with GenericResponse containing login information
     */
    @Override
    public ResponseEntity<GenericResponse> login(AccountDTO accountDTO) throws Exception {
        log.info("AccountService, login, accountDTO: {}", accountDTO);

        Account account = findByUsername(accountDTO.getUsername());
        validateAccountLogin(account, accountDTO.getPassword());

        // Create refresh token
        Token refreshToken = Token.builder()
                .accountId(account.getId())
                .type(TokenType.REFRESH_TOKEN)
                .tokenValue(createRefreshToken(account))
                .issuedAt(new Date())
                .expiredAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
                .ipAddress(accountDTO.getIpAddress())
                .build();
        tokenService.save(refreshToken);

        Map<String, Object> claims = Map.of(
                "id", account.getId(),
                "username", account.getUsername(),
                "email", account.getEmail(),
                "roles", account.getRoles().stream().map(Role::getRoleName).toList()
        );
        String accessToken = jwtService.generateAccessToken(claims, account.getUserId());

        Map<String, String> tokenMap = Map.of(
                "accessToken", accessToken,
                "accountId", String.valueOf(account.getId()),
                "username", account.getUsername(),
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
     * Register a new user account
     *
     * @param registerRequest Account data transfer object for registration
     * @return ResponseEntity with GenericResponse containing registration information
     */
    @Override
    public ResponseEntity<GenericResponse> userRegister(AccountDTO registerRequest) {

        //TODO
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(GenericResponse.builder()
                        .success(false)
                        .message("User registration is not implemented yet.")
                        .result(null)
                        .statusCode(HttpStatus.NOT_IMPLEMENTED.value())
                        .build());
    }

    /**
     * Create a JWT token for the account
     *
     * @param account Account entity
     * @return JWT token as a String
     * @throws Exception if token generation fails
     */
    private String createRefreshToken(Account account) throws Exception {
        Map<String, Object> claims = Map.of(
                "id", account.getId(),
                "username", account.getUsername(),
                "email", account.getEmail(),
                "roles", account.getRoles().stream().map(Role::getRoleName).toList()
        );
        return jwtService.generateRefreshToken(claims, account.getUserId());
    }
    /**
     * Validate account login credentials
     *
     * @param account      Account entity
     * @param rawPassword  Raw password to validate
     */
    private void validateAccountLogin(Account account, String rawPassword) {
        // Check if account exists
        if (account == null) {
            throw new RuntimeException(ACCOUNT_NOT_FOUND);
        }
        // Check if password matches
        if (!passwordEncoder.matches(rawPassword, account.getPassword())) {
            throw new RuntimeException(INVALID_PASSWORD);
        }
        // Check if account is active
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new RuntimeException(ACCOUNT_INACTIVE);
        }

    }
}

package vn.iotstar.authservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.micrometer.core.instrument.Counter;
import vn.iotstar.authservice.config.observability.AuthMetrics;
import vn.iotstar.authservice.model.dto.*;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.RoleRepository;
import vn.iotstar.authservice.repository.UserRepository;
import vn.iotstar.authservice.service.JwtService;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.service.RateLimiterService;
import vn.iotstar.authservice.service.TokenService;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.authservice.outbox.OutboxEventPublisher;
import vn.iotstar.authservice.exception.BadRequestException;
import vn.iotstar.authservice.exception.ForbiddenException;
import vn.iotstar.authservice.exception.ResourceNotFoundException;
import vn.iotstar.authservice.exception.TooManyRequestsException;
import vn.iotstar.authservice.exception.UserAlreadyExistsException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenService tokenService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private OtpService otpService;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private OutboxEventPublisher eventPublisher;
    @Mock private AuditLogger auditLogger;
    @Mock private AuthMetrics authMetrics;

    @InjectMocks
    private AuthServiceImpl authService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        Counter noopCounter = mock(Counter.class);
        lenient().when(authMetrics.getLoginSuccessCounter()).thenReturn(noopCounter);
        lenient().when(authMetrics.getLoginFailureCounter()).thenReturn(noopCounter);
        lenient().when(authMetrics.getRegisterSuccessCounter()).thenReturn(noopCounter);
        lenient().when(authMetrics.getPasswordResetCounter()).thenReturn(noopCounter);
        lenient().when(authMetrics.getRateLimitHitCounter()).thenReturn(noopCounter);

        Role userRole = new Role();
        userRole.setRoleName(RoleName.USER);

        dummyUser = new User();
        dummyUser.setId(UUID.randomUUID());
        dummyUser.setEmail("test@gmail.com");
        dummyUser.setUsername("testuser");
        dummyUser.setPassword("$2a$10$hashedPassword");
        dummyUser.setIsEmailVerified(true);
        dummyUser.setIsActive(true);
        dummyUser.setRoles(Set.of(userRole));
    }

    // =========================================================================
    // register tests
    // =========================================================================
    @Nested @DisplayName("register()")
    class RegisterTests {

        @Test @DisplayName("Duplicate email/username → UserAlreadyExistsException")
        void whenDuplicate_throwsConflict() {
            UserCreationRequest req = new UserCreationRequest("testuser", "test@gmail.com", "StrongP@ss1", "en");
            when(userRepository.existsByEmailOrUsername(any(), any())).thenReturn(true);
            assertThrows(UserAlreadyExistsException.class, () -> authService.register(req));
        }

        @Test @DisplayName("Role USER missing → ResourceNotFoundException")
        void whenRoleMissing_throwsNotFound() {
            UserCreationRequest req = new UserCreationRequest("newuser", "new@gmail.com", "StrongP@ss1", "en");
            when(userRepository.existsByEmailOrUsername(any(), any())).thenReturn(false);
            when(roleRepository.findByRoleName(RoleName.USER)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> authService.register(req));
        }

        @Test @DisplayName("Valid request → saves user and returns response")
        void whenValid_savesUser() {
            UserCreationRequest req = new UserCreationRequest("newuser", "new@gmail.com", "StrongP@ss1", "en");
            Role role = new Role(); role.setRoleName(RoleName.USER);
            when(userRepository.existsByEmailOrUsername(any(), any())).thenReturn(false);
            when(roleRepository.findByRoleName(RoleName.USER)).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(any())).thenReturn("$hashed");
            when(userRepository.save(any())).thenReturn(dummyUser);

            UserResponse resp = authService.register(req);
            assertNotNull(resp);
            verify(userRepository).save(any(User.class));
        }
    }

    // =========================================================================
    // login tests
    // =========================================================================
    @Nested @DisplayName("login()")
    class LoginTests {

        @Test @DisplayName("Rate limit exceeded → TooManyRequestsException")
        void whenRateLimitExceeded_throws429() {
            LoginRequest req = new LoginRequest("test@gmail.com", "password");
            doThrow(new TooManyRequestsException("Too many"))
                    .when(rateLimiterService).checkAndIncrement(any(), anyInt(), any());
            assertThrows(TooManyRequestsException.class, () -> authService.login(req));
        }

        @Test @DisplayName("Unverified email → ForbiddenException")
        void whenUnverified_throwsForbidden() {
            LoginRequest req = new LoginRequest("test@gmail.com", "password");
            when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));
            assertThrows(ForbiddenException.class, () -> authService.login(req));
        }

        @Test @DisplayName("Locked account → ForbiddenException")
        void whenLocked_throwsForbidden() {
            LoginRequest req = new LoginRequest("test@gmail.com", "password");
            when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));
            assertThrows(ForbiddenException.class, () -> authService.login(req));
        }

        @Test @DisplayName("Bad credentials → BadRequestException")
        void whenBadCredentials_throwsBadRequest() {
            LoginRequest req = new LoginRequest("test@gmail.com", "wrong");
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
            assertThrows(BadRequestException.class, () -> authService.login(req));
        }

        @Test @DisplayName("Success → returns AuthResponse and resets counter")
        void whenSuccess_returnsTokensAndResetsCounter() {
            LoginRequest req = new LoginRequest("test@gmail.com", "correctP@ss");
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(dummyUser);
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(jwtService.generateToken(any())).thenReturn("access-token");
            Token refreshToken = mock(Token.class);
            when(refreshToken.getTokenValue()).thenReturn("refresh-token");
            when(tokenService.createRefreshToken(any())).thenReturn(refreshToken);
            when(jwtService.getJwtExpiration()).thenReturn(900000L);

            AuthResponse resp = authService.login(req);

            assertEquals("access-token", resp.getAccessToken());
            verify(rateLimiterService).reset(any());
        }
    }

    // =========================================================================
    // refreshToken tests
    // =========================================================================
    @Nested @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test @DisplayName("Invalid token → BadRequestException")
        void whenInvalid_throws() {
            when(tokenService.validateRefreshToken(any())).thenReturn(Optional.empty());
            assertThrows(BadRequestException.class, () -> authService.refreshToken("invalid"));
        }

        @Test @DisplayName("Valid token → new access token")
        void whenValid_returnsNewAccessToken() {
            Token token = mock(Token.class);
            when(token.getUser()).thenReturn(dummyUser);
            when(tokenService.validateRefreshToken(any())).thenReturn(Optional.of(token));
            when(jwtService.generateToken(any())).thenReturn("new-access-token");
            when(jwtService.getJwtExpiration()).thenReturn(900000L);

            AuthResponse resp = authService.refreshToken("valid-refresh");
            assertEquals("new-access-token", resp.getAccessToken());
        }
    }

    // =========================================================================
    // resendRegistrationOtp tests
    // =========================================================================
    @Nested @DisplayName("resendRegistrationOtp()")
    class ResendOtpTests {

        @Test @DisplayName("Email not found → BadRequestException")
        void whenUserNotFound_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
            assertThrows(BadRequestException.class,
                    () -> authService.resendRegistrationOtp(new EmailRequest("x@x.com", "en"), "id"));
        }

        @Test @DisplayName("Already verified → BadRequestException")
        void whenAlreadyVerified_throws() {
            dummyUser.setIsEmailVerified(true);
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            assertThrows(BadRequestException.class,
                    () -> authService.resendRegistrationOtp(new EmailRequest("test@gmail.com", "en"), "id"));
        }

        @Test @DisplayName("Valid → dispatches OTP")
        void whenValid_dispatches() {
            dummyUser.setIsEmailVerified(false);
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            authService.resendRegistrationOtp(new EmailRequest("test@gmail.com", "en"), "corr");
            verify(otpService).generateAndDispatch(any(), any(), any(), any());
        }
    }

    // =========================================================================
    // resetPassword tests
    // =========================================================================
    @Nested @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test @DisplayName("Invalid OTP → BadRequestException")
        void whenInvalidOtp_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(any(), any())).thenReturn(false);
            assertThrows(BadRequestException.class, () ->
                    authService.resetPassword(new ResetPasswordRequest("test@gmail.com", "000000", "New@Pass1", "New@Pass1")));
        }

        @Test @DisplayName("Passwords mismatch → BadRequestException")
        void whenPasswordsMismatch_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(any(), any())).thenReturn(true);
            assertThrows(BadRequestException.class, () ->
                    authService.resetPassword(new ResetPasswordRequest("test@gmail.com", "123456", "New@Pass1", "Different1!")));
        }

        @Test @DisplayName("Same as old password → BadRequestException")
        void whenSameAsOld_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(any(), any())).thenReturn(true);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            assertThrows(BadRequestException.class, () ->
                    authService.resetPassword(new ResetPasswordRequest("test@gmail.com", "123456", "New@Pass1", "New@Pass1")));
        }

        @Test @DisplayName("Valid reset → saves new password")
        void whenValid_savesNewPassword() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(any(), any())).thenReturn(true);
            when(passwordEncoder.matches(any(), any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("$encoded");
            authService.resetPassword(new ResetPasswordRequest("test@gmail.com", "123456", "New@Pass1", "New@Pass1"));
            verify(userRepository).save(dummyUser);
        }
    }

    // =========================================================================
    // changePassword tests
    // =========================================================================
    @Nested @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test @DisplayName("Wrong current password → BadRequestException")
        void whenWrongCurrent_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);
            assertThrows(BadRequestException.class, () ->
                    authService.changePassword(new ChangePasswordRequest("wrong", "New@Pass1", "New@Pass1"), "test@gmail.com"));
        }

        @Test @DisplayName("New == current → BadRequestException")
        void whenNewSameCurrent_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches(eq("same"), any())).thenReturn(true);
            assertThrows(BadRequestException.class, () ->
                    authService.changePassword(new ChangePasswordRequest("same", "same", "same"), "test@gmail.com"));
        }

        @Test @DisplayName("Confirm mismatch → BadRequestException")
        void whenConfirmMismatch_throws() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches(eq("curr"), any())).thenReturn(true);
            assertThrows(BadRequestException.class, () ->
                    authService.changePassword(new ChangePasswordRequest("curr", "New@Pass1", "Different1!"), "test@gmail.com"));
        }
    }
}

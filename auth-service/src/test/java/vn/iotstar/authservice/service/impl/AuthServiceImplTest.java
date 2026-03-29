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
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.iotstar.authservice.model.dto.ChangePasswordRequest;
import vn.iotstar.authservice.model.dto.EmailRequest;
import vn.iotstar.authservice.model.dto.ResetPasswordRequest;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.RoleRepository;
import vn.iotstar.authservice.repository.UserRepository;
import vn.iotstar.authservice.service.JwtService;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.service.TokenService;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenService tokenService;
    @Mock private OtpService otpService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = new User();
        dummyUser.setId(UUID.randomUUID());
        dummyUser.setEmail("test@gmail.com");
        dummyUser.setPassword("$2a$10$hashedOldPassword"); // BCrypt hash
    }

    // =========================================================================
    // resendRegistrationOtp tests
    // =========================================================================
    @Nested
    @DisplayName("resendRegistrationOtp()")
    class ResendOtpTests {

        @Test
        @DisplayName("TC-01: Email not found → BadRequestException")
        void whenUserNotFound_throwsException() {
            EmailRequest req = new EmailRequest("notfound@gmail.com", "en");
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());
            assertThrows(BadRequestException.class, () -> authService.resendRegistrationOtp(req, "corr-id"));
        }

        @Test
        @DisplayName("TC-02: Already verified → BadRequestException")
        void whenUserAlreadyVerified_throwsException() {
            EmailRequest req = new EmailRequest("test@gmail.com", "en");
            dummyUser.setIsEmailVerified(true);
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));
            assertThrows(BadRequestException.class, () -> authService.resendRegistrationOtp(req, "corr-id"));
        }

        @Test
        @DisplayName("TC-03: Valid request → dispatches OTP")
        void whenValid_dispatchesOtp() {
            EmailRequest req = new EmailRequest("test@gmail.com", "en");
            dummyUser.setIsEmailVerified(false);
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));

            authService.resendRegistrationOtp(req, "corr-id");

            verify(otpService, times(1)).generateAndDispatch(
                    eq(dummyUser.getId().toString()),
                    eq(dummyUser.getEmail()),
                    eq("en"),
                    eq("corr-id"));
        }
    }

    // =========================================================================
    // resetPassword tests — verifies BUG-01, BUG-02, BUG-03 fixes
    // =========================================================================
    @Nested
    @DisplayName("resetPassword() — BUG-01/02/03 fixes")
    class ResetPasswordTests {

        @Test
        @DisplayName("TC-04: Invalid OTP → BadRequestException (BUG-02 fix)")
        void whenOtpInvalid_throwsException() {
            ResetPasswordRequest req = new ResetPasswordRequest(
                    "test@gmail.com", "wrong-otp", "newPass123", "newPass123");
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(req.email(), req.otp())).thenReturn(false);

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> authService.resetPassword(req));
            assertTrue(ex.getMessage().contains("Invalid or expired OTP"));
        }

        @Test
        @DisplayName("TC-05: Passwords don't match → BadRequestException (BUG-03 fix)")
        void whenPasswordsDontMatch_throwsException() {
            ResetPasswordRequest req = new ResetPasswordRequest(
                    "test@gmail.com", "123456", "newPass123", "differentPass");
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(req.email(), req.otp())).thenReturn(true);

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> authService.resetPassword(req));
            assertTrue(ex.getMessage().contains("do not match"));
        }

        @Test
        @DisplayName("TC-06: Same as old password → BadRequestException (BUG-01 fix)")
        void whenSameAsOldPassword_throwsException() {
            ResetPasswordRequest req = new ResetPasswordRequest(
                    "test@gmail.com", "123456", "oldPassword", "oldPassword");
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(req.email(), req.otp())).thenReturn(true);
            when(passwordEncoder.matches("oldPassword", dummyUser.getPassword())).thenReturn(true);

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> authService.resetPassword(req));
            assertTrue(ex.getMessage().contains("same as the old password"));
        }

        @Test
        @DisplayName("TC-07: Valid reset → password encoded and saved")
        void whenValid_encodesAndSaves() {
            ResetPasswordRequest req = new ResetPasswordRequest(
                    "test@gmail.com", "123456", "brandNewPass", "brandNewPass");
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(dummyUser));
            when(otpService.verify(req.email(), req.otp())).thenReturn(true);
            when(passwordEncoder.matches("brandNewPass", dummyUser.getPassword())).thenReturn(false);
            when(passwordEncoder.encode("brandNewPass")).thenReturn("$2a$10$hashedNewPassword");

            authService.resetPassword(req);

            verify(passwordEncoder).encode("brandNewPass");
            verify(userRepository).save(dummyUser);
            assertEquals("$2a$10$hashedNewPassword", dummyUser.getPassword());
        }
    }

    // =========================================================================
    // changePassword tests
    // =========================================================================
    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("TC-08: Wrong current password → BadRequestException")
        void whenCurrentPasswordWrong_throwsException() {
            ChangePasswordRequest req = new ChangePasswordRequest("wrongPass", "newPass", "newPass");
            when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches("wrongPass", dummyUser.getPassword())).thenReturn(false);

            assertThrows(BadRequestException.class,
                    () -> authService.changePassword(req, "test@gmail.com"));
        }

        @Test
        @DisplayName("TC-09: New == current → BadRequestException")
        void whenNewSameAsCurrent_throwsException() {
            ChangePasswordRequest req = new ChangePasswordRequest("currentPass", "currentPass", "currentPass");
            when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches("currentPass", dummyUser.getPassword())).thenReturn(true);

            assertThrows(BadRequestException.class,
                    () -> authService.changePassword(req, "test@gmail.com"));
        }

        @Test
        @DisplayName("TC-10: Confirm doesn't match → BadRequestException")
        void whenConfirmDoesntMatch_throwsException() {
            ChangePasswordRequest req = new ChangePasswordRequest("currentPass", "newPass", "differentPass");
            when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(dummyUser));
            when(passwordEncoder.matches("currentPass", dummyUser.getPassword())).thenReturn(true);

            assertThrows(BadRequestException.class,
                    () -> authService.changePassword(req, "test@gmail.com"));
        }
    }

    // =========================================================================
    // processOAuth2Login — BUG-05 fix
    // =========================================================================
    @Test
    @DisplayName("TC-11: OAuth2 login → UnsupportedOperationException (BUG-05 fix)")
    void processOAuth2Login_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> authService.processOAuth2Login("google", "auth-code"));
    }
}

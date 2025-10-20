package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.authservice.mapper.UserMapper;
import vn.iotstar.authservice.model.dto.*;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.RoleRepository;
import vn.iotstar.authservice.repository.UserRepository;
import vn.iotstar.authservice.service.AuthService;
import vn.iotstar.authservice.service.JwtService;
import vn.iotstar.authservice.service.OtpService;
import vn.iotstar.authservice.service.TokenService;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.authservice.util.TopicName;
import vn.iotstar.utils.dto.UserRegister;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.UserAlreadyExistsException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OtpService otpService;

    @Override
    @Transactional
    public UserResponse register(UserCreationRequest request) {
        log.info("Registering user: {}", request);
        if (userRepository.existsByEmailOrUsername(request.email(),request.username())) {
            throw new UserAlreadyExistsException("Username hoặc email đã tồn tại");
        }

        User newUser = UserMapper.toUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));

        Role userRole = roleRepository.findByRoleName(RoleName.USER)
                .orElseThrow(() -> new RuntimeException(" Role 'USER' is not found."));
        newUser.setRoles(Set.of(userRole));

        User savedUser = userRepository.save(newUser);
        return UserMapper.toUserResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Processing login for user: {}", request.emailOrUsername());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.emailOrUsername(), request.password())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = (User) authentication.getPrincipal();

        String accessToken = jwtService.generateToken(user);
        Token refreshToken = tokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getTokenValue())
                .expiresIn(jwtService.getJwtExpiration())
                .userProfile(UserMapper.toUserResponse(user))
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshTokenValue) {
        log.info("Processing refresh token for user: {}", refreshTokenValue);

        Optional<Token> refreshToken = tokenService.validateRefreshToken(refreshTokenValue);
        if (refreshToken.isEmpty()) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        User user = refreshToken.get().getUser();

        String newAccessToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtService.getJwtExpiration())
                .userProfile(UserMapper.toUserResponse(user))
                .build();
    }

    @Override
    public AuthResponse processOAuth2Login(String providerName, String code) {
        return null;
    }

    @Override
    public void logout(String refreshTokenValue, String subject) {
        tokenService.revokeToken(refreshTokenValue,subject);
    }

    @Override
    public void forgotPassword(EmailRequest emailRequest, String correlationId) {
        log.info("Processing forgot password for email: {}", emailRequest.email());
        User user = userRepository.findByEmail(emailRequest.email())
                .orElseThrow(() -> new BadRequestException("Email not found"));
        otpService.generateAndDispatch(
                String.valueOf(user.getId()),
                user.getEmail(),
                emailRequest.locale(),
                correlationId);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
        log.info("Resetting password for user: {}", resetPasswordRequest.email());
        User user = userRepository.findByEmail(resetPasswordRequest.email())
                .orElseThrow(() -> new BadRequestException("Email not found"));

        if(!resetPasswordRequest.newPassword().equals(user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }

        user.setPassword(passwordEncoder.encode(resetPasswordRequest.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest changePasswordRequest, String subject) {
        log.info("Changing password for user: {}", subject);
        User user = userRepository.findByEmail(subject)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(changePasswordRequest.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (changePasswordRequest.newPassword().equals(changePasswordRequest.currentPassword())) {
            throw new BadRequestException("New password cannot be the same as the current password");
        }
        if(!changePasswordRequest.newPassword().equals(changePasswordRequest.confirmNewPassword())) {
            throw new BadRequestException("New password and confirmation do not match");
        }
        user.setPassword(passwordEncoder.encode(changePasswordRequest.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void activateAccount(String email) {
        log.info("Activating account for email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        user.setIsEmailVerified(true);
        userRepository.save(user);
        UserRegister userRegister = new UserRegister(
                user.getUsername(),
                user.getEmail()
        );
        kafkaTemplate.send(TopicName.ACTIVATE_ACCOUNT, userRegister);
    }

}
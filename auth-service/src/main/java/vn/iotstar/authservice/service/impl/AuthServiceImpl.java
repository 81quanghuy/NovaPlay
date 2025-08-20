package vn.iotstar.authservice.service.impl;

import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import vn.iotstar.authservice.service.IAuthService;
import vn.iotstar.authservice.service.IJwtService;
import vn.iotstar.authservice.service.ITokenService;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.UserAlreadyExistsException;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final IJwtService jwtService;
    private final ITokenService tokenService;

    @Override
    @Transactional
    public UserResponse register(UserCreationRequest request) {
        log.info("Registering user: {}", request);
        if (userRepository.existsByEmailOrUsername(request.email(),request.username())) {
            throw new UserAlreadyExistsException("Error: Username or Email is already in use!");
        }

        User newUser = UserMapper.toUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));

        Role userRole = roleRepository.findByRoleName(RoleName.USER)
                .orElseThrow(() -> new RuntimeException("Error: Role 'USER' is not found."));
        newUser.setRoles(Set.of(userRole));

        User savedUser = userRepository.save(newUser);
        return UserMapper.toUserResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Processing login for user: {}", request.username());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
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
    public AuthResponse refreshToken(String refreshTokenValue, String subject) {
        log.info("Processing refresh token for user: {}", refreshTokenValue);

        Optional<Token> refreshToken = tokenService.validateRefreshToken(refreshTokenValue);
        if (refreshToken.isEmpty()) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        User user = refreshToken.get().getUser();
        if (!user.getEmail().equals(subject)) {
            throw new BadRequestException("Refresh token does not belong to the current user");
        }

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
    public void forgotPassword(EmailRequest emailRequest) {
        log.info("Sending OTP for user: {}", emailRequest.email());
        if (!userRepository.existsByEmail(emailRequest.email())) {
            throw new BadRequestException("Email not found");
        }
        sendOTP(emailRequest.email());
    }

    @Override
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

    private void sendOTP(String email) {
        log.info("Sending OTP to email: {}", email);
    }

}
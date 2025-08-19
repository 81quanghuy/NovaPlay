package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.authservice.jwt.service.JwtService;
import vn.iotstar.authservice.mapper.UserMapper;
import vn.iotstar.authservice.model.dto.*;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.RoleRepository;
import vn.iotstar.authservice.repository.UserRepository;
import vn.iotstar.authservice.service.IAuthService;
import vn.iotstar.authservice.service.ITokenService;
import vn.iotstar.authservice.util.RoleName;
import vn.iotstar.utils.exceptions.wrapper.UserAlreadyExistsException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ITokenService tokenService;

    @Override
    @Transactional
    public UserResponse register(UserCreationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Error: Email is already in use!");
        }

        User newUser = UserMapper.toUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));

        Role userRole = roleRepository.findByRoleName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Role 'USER' is not found."));
        newUser.setRoles(Set.of(userRole));

        User savedUser = userRepository.save(newUser);
        return UserMapper.toUserResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + request.email()));

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
        return tokenService.validateRefreshToken(refreshTokenValue)
                .map(token -> {
                    User user = token.getUser();
                    String newAccessToken = jwtService.generateToken(user);
                    return AuthResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(refreshTokenValue)
                            .expiresIn(jwtService.getJwtExpiration())
                            .userProfile(UserMapper.toUserResponse(user))
                            .build();
                })
                .orElseThrow(() -> new RuntimeException("Invalid or expired refresh token."));
    }

    @Override
    public AuthResponse processOAuth2Login(String providerName, String code) {
        return null;
    }

    @Override
    public void logout(String refreshTokenValue) {
        tokenService.revokeToken(refreshTokenValue);
    }
}
package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.dto.AuthResponse; // DTO containing access & refresh tokens
import vn.iotstar.authservice.model.dto.LoginRequest; // DTO containing email/password
import vn.iotstar.authservice.model.dto.UserCreationRequest;
import vn.iotstar.authservice.model.dto.UserResponse;

public interface IAuthService {

    /**
     * Handles the business logic for user registration.
     * @param request A DTO containing registration details.
     * @return A UserResponse with the new user's information.
     */
    UserResponse register(UserCreationRequest request);

    /**
     * Handles the login process using email and password.
     * @param request A DTO containing login credentials.
     * @return an AuthResponse containing the Access and Refresh Tokens.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Handles the logic for refreshing an access token using a refresh token.
     * @param refreshTokenValue The value of the refresh token.
     * @return an AuthResponse containing the new Access Token.
     */
    AuthResponse refreshToken(String refreshTokenValue);

    /**
     * Processes an OAuth2 login flow.
     * @param providerName The name of the provider (e.g., 'google').
     * @param code The authorization code from the provider.
     * @return an AuthResponse containing the Access and Refresh Tokens.
     */
    AuthResponse processOAuth2Login(String providerName, String code);

    /**
     * Handles the logout process by revoking the refresh token.
     * @param refreshTokenValue The refresh token to be revoked.
     */
    void logout(String refreshTokenValue);
}
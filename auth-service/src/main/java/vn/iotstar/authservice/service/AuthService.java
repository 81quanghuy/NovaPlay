package vn.iotstar.authservice.service;

import jakarta.validation.Valid;
import vn.iotstar.authservice.model.dto.*;

public interface AuthService {

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
     * @param subject The subject of the access token, typically the user ID or username.
     * @param refreshTokenValue The refresh token to be revoked.
     */
    void logout(String refreshTokenValue, String subject);

    /**
     * Sends an OTP (One-Time Password) to the user's email for verification.
     *
     * @param emailRequest A DTO containing the user's email address.
     * @param correlationId A unique identifier for tracing the request.
     */
    void forgotPassword(@Valid EmailRequest emailRequest, String correlationId);

    /**
     * Resets the user's password using the provided reset token and new password.
     * @param resetPasswordRequest A DTO containing the reset token and new password.
     */
    void resetPassword(@Valid ResetPasswordRequest resetPasswordRequest);

    /**
     * Changes the user's password.
     * @param changePasswordRequest A DTO containing the current password and new password.
     * @param subject The subject of the access token, typically the user ID or username.
     */
    void changePassword(@Valid ChangePasswordRequest changePasswordRequest, String subject);

    /**
     * Activates a user account using the provided activation token.
     * @param email The email address associated with the account to be activated.
     */
    void activateAccount(String email);
}
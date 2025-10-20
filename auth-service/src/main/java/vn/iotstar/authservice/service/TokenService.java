package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import java.util.Optional;

public interface TokenService {

    /**
     * Creates and persists a new refresh token for a user.
     * @param user The user entity.
     * @return The created Token entity.
     */
    Token createRefreshToken(User user);

    /**
     * Validates a refresh token.
     * @param tokenValue The value of the refresh token.
     * @return an Optional containing the Token entity if the token is valid and not expired.
     */
    Optional<Token> validateRefreshToken(String tokenValue);

    /**
     * Revokes a token (e.g., during logout).
     * @param object jwt subject or user identifier, used to identify the user or session.
     * @param tokenValue The value of the token to revoke.
     */
    void revokeToken(String tokenValue,String object);
}
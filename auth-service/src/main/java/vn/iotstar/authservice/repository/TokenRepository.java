package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Token;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {

    /**
     * Finds a token by its value.
     * @param tokenValue The token value to search for.
     * @return an Optional containing the found Token.
     */
    Optional<Token> findByTokenValue(String tokenValue);

    /**
     * Finds all valid (not revoked) tokens for a specific user.
     * @param userId The ID of the user.
     * @return A list of valid tokens.
     */
    List<Token> findAllByUser_IdAndIsRevokedIsFalse(UUID userId);
}
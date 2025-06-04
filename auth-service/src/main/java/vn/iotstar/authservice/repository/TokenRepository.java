package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.util.TokenType;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    Optional<Token> findByTokenValue(String token);

    Optional<Token> findByTokenValueAndUserId(String token, String userId);

    Optional<Token> findByUserId(String userId);

    Optional<Token> findByTokenValueAndUserIdAndType(String tokenValue, String userId, TokenType type);
}

package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.TokenRepository;
import vn.iotstar.authservice.service.ITokenService;
import vn.iotstar.authservice.util.TokenType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements ITokenService {

    private final TokenRepository tokenRepository;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Override
    public Token createRefreshToken(User user) {
        revokeAllUserTokens(user);

        Token token = Token.builder()
                .user(user)
                .isRevoked(false)
                .tokenValue(UUID.randomUUID().toString())
                .expiredAt(Instant.now().plusMillis(refreshTokenExpiration))
                .type(TokenType.REFRESH_TOKEN)
                .build();
        return tokenRepository.save(token);
    }

    @Override
    public Optional<Token> validateRefreshToken(String tokenValue) {
        return tokenRepository.findByTokenValue(tokenValue)
                .filter(token -> !token.getIsRevoked() && token.getExpiredAt().isAfter(Instant.now()));
    }

    @Override
    public void revokeToken(String tokenValue) {
        tokenRepository.findByTokenValue(tokenValue).ifPresent(token -> {
            token.setIsRevoked(true);
            tokenRepository.save(token);
        });
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllByUser_IdAndIsRevokedIsFalse(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> token.setIsRevoked(true));
        tokenRepository.saveAll(validUserTokens);
    }
}
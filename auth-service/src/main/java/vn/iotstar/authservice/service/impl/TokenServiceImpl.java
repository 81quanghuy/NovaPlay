package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
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
@Slf4j
@RequiredArgsConstructor
public class TokenServiceImpl implements ITokenService {

    private final TokenRepository tokenRepository;

    @Value("${spring.application.security.jwt.refresh-token.expiration}")
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
    public void revokeToken(String tokenValue, String subject) {
        log.info("Attempting to revoke refresh token for subject: '{}'", subject);
        validateRefreshToken(tokenValue)
                .ifPresent(rt -> {
                    if (!subject.equals(rt.getUser().getEmail())) {
                        throw new AccessDeniedException("Refresh token does not belong to current user");
                    }
                    if (Boolean.TRUE.equals(!rt.getIsRevoked()) && rt.getExpiredAt().isAfter(Instant.now())) {
                        revokeToken(rt);
                    }
                });
    }

    private void revokeToken(Token token) {
        log.info("Revoking refresh token: '{}'", token.getTokenValue());
        token.setIsRevoked(true);
        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllByUser_IdAndIsRevokedIsFalse(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> token.setIsRevoked(true));
        tokenRepository.saveAll(validUserTokens);
    }
}
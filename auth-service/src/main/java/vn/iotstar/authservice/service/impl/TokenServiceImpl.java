package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.repository.TokenRepository;
import vn.iotstar.authservice.service.TokenService;
import vn.iotstar.authservice.util.TokenType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final TokenRepository tokenRepository;

    @Value("${jwt.refresh-token.expiration}")
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
                    revokeToken(rt);
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

    /**
     * Dọn token đã hết hạn, mỗi giờ một lần.
     * <p>
     * Cố ý KHÔNG có distributed lock dù chạy trên mọi pod (replicas=2, HPA tới 6): đây là một câu
     * DELETE đơn, idempotent, dựa trên index {@code idx_token_expired_at}. Pod thứ hai chạy ngay
     * sau pod thứ nhất chỉ đơn giản là không thấy row nào. Thêm ShedLock ở đây sẽ phải trả giá
     * bằng một bảng lock và một connection tới Postgres managed (vốn bị giới hạn chặt) để đổi lấy
     * thứ gần như không đáng gì.
     * <p>
     * Bản trước đọc {@code findAll()} rồi lọc bằng Java — kéo toàn bộ bảng token vào heap mỗi giờ
     * trên mọi pod, rồi sinh N câu DELETE riêng lẻ.
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteAllExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Đã xoá {} token hết hạn", deleted);
        }
    }
}
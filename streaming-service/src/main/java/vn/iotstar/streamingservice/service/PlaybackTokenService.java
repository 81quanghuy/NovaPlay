package vn.iotstar.streamingservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.exception.UnauthorizedPlaybackException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT ngắn hạn, KHÔNG liên quan gì tới JWT đăng nhập của auth-service — chỉ chứng minh "người này
 * đã được streaming-service cấp quyền phát bộ phim/tập cụ thể này", dùng để bảo vệ
 * {@code /api/v1/streaming/hls/**} (path {@code permitAll} ở tầng Spring Security vì trình phát
 * HLS không tự gắn được header Authorization — xem SecurityConfig).
 */
@Component
public class PlaybackTokenService {

    private static final String CLAIM_MANIFEST_ID = "manifestId";

    private final SecretKey key;
    private final Duration ttl;

    public PlaybackTokenService(
            @Value("${playback.token-secret}") String base64Secret,
            @Value("${playback.token-ttl-hours:4}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(Base64Decode(base64Secret));
        this.ttl = Duration.ofHours(ttlHours);
    }

    private static byte[] Base64Decode(String base64Secret) {
        return java.util.Base64.getDecoder().decode(base64Secret);
    }

    public String mint(String userEmail, String manifestId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userEmail)
                .claim(CLAIM_MANIFEST_ID, manifestId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** @return Claims đã xác thực chữ ký + hạn dùng + khớp đúng manifestId đang truy cập. */
    public Claims validate(String token, String expectedManifestId) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedPlaybackException("Missing playback token.");
        }
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedPlaybackException("Playback token expired.");
        } catch (JwtException e) {
            throw new UnauthorizedPlaybackException("Invalid playback token.");
        }
        String tokenManifestId = claims.get(CLAIM_MANIFEST_ID, String.class);
        if (!expectedManifestId.equals(tokenManifestId)) {
            throw new UnauthorizedPlaybackException("Playback token does not grant access to this manifest.");
        }
        return claims;
    }

    /** Thời gian còn hiệu lực của token — dùng để cap TTL của presigned GET URL cấp cho segment. */
    public Duration remainingValidity(Claims claims) {
        Instant expiry = claims.getExpiration().toInstant();
        Duration remaining = Duration.between(Instant.now(), expiry);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}

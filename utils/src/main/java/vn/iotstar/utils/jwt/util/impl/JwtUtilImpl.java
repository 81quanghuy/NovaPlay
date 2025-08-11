package vn.iotstar.utils.jwt.util.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import vn.iotstar.utils.config.JwtConfig;
import vn.iotstar.utils.jwt.util.JwtUtil;

import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtilImpl implements JwtUtil {

    private static final Long JWT_ACCESS_EXPIRATION = 3600000L;
    private static final Long JWT_REFRESH_EXPIRATION = 604800000L;
    private final JwtConfig jwtConfig;

    private PublicKey getPublicKey() {
        try {
            return jwtConfig.getPublicKey();
        } catch (Exception e) {
            log.error("Error retrieving public key: {}", e.getMessage());
            throw new IllegalStateException("Failed to retrieve public key for JWT validation", e);
        }
    }
    @Override
    public String extractUserId(final String token) {
        return this.extractClaims(token, Claims::getSubject);
    }

    @Override
    public Date extractExpiration(final String token) {
        return this.extractClaims(token, Claims::getExpiration);
    }

    @Override
    public <T> T extractClaims(final String token, Function<Claims, T> claimsResolver) {
        final Claims claims = this.extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(this.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(final String token) {
        return this.extractExpiration(token).before(new Date());
    }

    @Override
    public Boolean validateToken(final String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getPublicKey())
                    .build()
                    .parseClaimsJws(token);

            return !this.isTokenExpired(token);
        } catch (JwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty.");
        }
        return false;
    }

    /**
     * Generates an access token with the given claims and user ID.
     * This method is a placeholder and should be implemented according to your application's requirements.
     *
     * @param claim  The claims to include in the token.
     * @param userId The user ID for whom the token is generated.
     * @return A string representing the generated access token.
     */
    @Override
    public String generateAccessToken(Map<String, Object> claim, String userId) {
        return Jwts.builder()
                .setClaims(claim)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JWT_ACCESS_EXPIRATION )) // 1 hour
                .signWith(jwtConfig.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Generates a refresh token with the given claims and user ID.
     * This method is a placeholder and should be implemented according to your application's requirements.
     *
     * @param claim  The claims to include in the token.
     * @param userId The user ID for whom the token is generated.
     * @return A string representing the generated refresh token.
     */
    @Override
    public String generateRefreshToken(Map<String, Object> claim, String userId){
        return Jwts.builder()
                .setClaims(claim)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JWT_REFRESH_EXPIRATION)) // 7 days
                .signWith(jwtConfig.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }
}

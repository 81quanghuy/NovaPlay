package vn.iotstar.utils.jwt.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import vn.iotstar.utils.config.JwtConfig;
import vn.iotstar.utils.jwt.JwtUtil;

import java.security.PublicKey;
import java.util.Date;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtilImpl implements JwtUtil {

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
    public String extractUsername(final String token) {
        return this.extractClaims(token, Claims::getSubject);
    }

    @Override
    public Date extractExpiration(final String token) {
        return this.extractClaims(token, Claims::getExpiration);
    }

    private  <T> T extractClaims(final String token, Function<Claims, T> claimsResolver) {
        final Claims claims = this.extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @Override
    public Claims extractAllClaims(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(this.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public Boolean validateToken(final String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getPublicKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    private Boolean isTokenExpired(final String token) {
        final Date expiration = this.extractExpiration(token);
        return expiration.before(new Date());
    }

    /**
     * Validates the token and checks if it belongs to the given user.
     *
     * @param token The JWT token.
     * @param userDetails The user details object from Spring Security.
     * @return true if the token is valid and belongs to the user.
     */
    public Boolean validateToken(final String token, final UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch(Exception e) {
            return false;
        }
    }
}

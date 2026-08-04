package vn.iotstar.apigateway.jwt.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import vn.iotstar.apigateway.jwt.JwtUtil;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtilImpl implements JwtUtil {

    private final RSAPublicKey publicKey;

    @Value("${auth.jwt.issuer:novaplay-auth}")
    private String expectedIssuer;

    @Value("${auth.jwt.audience:novaplay}")
    private String expectedAudience;

    @Override
    public String extractUsername(final String token) {
        return this.extractClaims(token, Claims::getSubject);
    }

    @Override
    public Date extractExpiration(final String token) {
        return this.extractClaims(token, Claims::getExpiration);
    }

    private <T> T extractClaims(final String token, Function<Claims, T> claimsResolver) {
        final Claims claims = this.extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @Override
    public Claims extractAllClaims(final String token) {
        return Jwts.parser()
                .verifyWith(this.publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public Boolean validateToken(final String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(publicKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

            String iss = claims.getIssuer();
            Set<String> aud = claims.getAudience();

            if (!expectedIssuer.equals(iss)) {
                log.error("JWT issuer mismatch: expected={}, got={}", expectedIssuer, iss);
                return false;
            }
            if (aud == null || !aud.contains(expectedAudience)) {
                log.error("JWT audience mismatch: expected={}, got={}", expectedAudience, aud);
                return false;
            }

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

    public Boolean validateToken(final String token, final UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            return false;
        }
    }
}

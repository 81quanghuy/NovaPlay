package vn.iotstar.apigateway.jwt.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import vn.iotstar.apigateway.jwt.JwtUtil;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtilImpl implements JwtUtil {

    private final RSAPublicKey publicKey;

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
                .setSigningKey(this.publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public Boolean validateToken(final String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(this.publicKey)
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

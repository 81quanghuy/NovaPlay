package vn.iotstar.authservice.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.service.JwtService;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final RSAPrivateKey rsaPrivateKey;
    private final RSAPublicKey rsaPublicKey;

    @Value("${spring.application.security.jwt.expiration}")
    private long accessTokenExpiration;

    @Value("${auth.jwt.issuer:novaplay-auth}")
    private String issuer;

    @Value("${auth.jwt.audience:novaplay}")
    private String audience;

    @Value("${auth.jwt.kid:v1}")
    private String kid;

    @Override
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        var roles = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getRoleName().name())
                .collect(Collectors.toSet());
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuer(issuer)
                .setAudience(audience)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .setHeaderParam("kid", kid)
                .signWith(this.rsaPrivateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    @Override
    public long getJwtExpiration() {
        return this.accessTokenExpiration;
    }

    @Override
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            return !expiration.before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> extractRoles(String token) {
        return (List<String>) extractAllClaims(token).get("roles", List.class);
    }

    @Override
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    @Override
    public String extractIssuer(String token) {
        return extractAllClaims(token).getIssuer();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(rsaPublicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

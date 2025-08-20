package vn.iotstar.authservice.service.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.service.IJwtService;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements IJwtService {

    private final RSAPrivateKey rsaPrivateKey;

    @Value("${spring.application.security.jwt.expiration}")
    private long accessTokenExpiration;

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
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(this.rsaPrivateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    @Override
    public long getJwtExpiration() {
        return this.accessTokenExpiration;
    }
}
package vn.iotstar.authservice.jwt.util;

import io.jsonwebtoken.Claims;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

public interface JwtUtil {

    String extractUserId(final String token);

    Date extractExpiration(final String token);

    <T> T extractClaims(final String token, final Function<Claims, T> claimsResolver);

    Boolean validateToken(final String token);

    String generateAccessToken(Map<String, Object> claim, String userId) ;

    String generateRefreshToken(Map<String, Object> claim, String userId) ;
}

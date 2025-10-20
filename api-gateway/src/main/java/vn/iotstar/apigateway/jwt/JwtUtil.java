package vn.iotstar.apigateway.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;

public interface JwtUtil {

    String extractUsername(final String token);

    Date extractExpiration(final String token);

    Boolean validateToken(final String token);

    Boolean validateToken(String token, UserDetails userDetails);

    Claims extractAllClaims(String token);
}

package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.entity.User;

import java.util.Date;
import java.util.List;

public interface JwtService {

    String generateToken(User user);

    long getJwtExpiration();

    String extractEmail(String token);

    boolean isTokenValid(String token);

    List<String> extractRoles(String token);

    String extractJti(String token);

    String extractIssuer(String token);

    Date extractExpiration(String token);
}

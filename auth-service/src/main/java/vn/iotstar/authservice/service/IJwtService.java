package vn.iotstar.authservice.service;


import org.springframework.security.core.userdetails.UserDetails;
import vn.iotstar.authservice.model.entity.User;

public interface IJwtService {

    String generateToken(User user) ;

    long getJwtExpiration();

    String extractEmail(String token);

    boolean isTokenValid(String token);
}











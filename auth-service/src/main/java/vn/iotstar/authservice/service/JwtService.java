package vn.iotstar.authservice.service;


import vn.iotstar.authservice.model.entity.User;

public interface JwtService {

    String generateToken(User user) ;

    long getJwtExpiration();

    String extractEmail(String token);

    boolean isTokenValid(String token);
}











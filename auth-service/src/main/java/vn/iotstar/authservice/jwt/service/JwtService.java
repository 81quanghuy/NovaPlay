package vn.iotstar.authservice.jwt.service;


import vn.iotstar.authservice.model.entity.User;

public interface JwtService {

    public String generateToken(User user) ;

    long getJwtExpiration();
}











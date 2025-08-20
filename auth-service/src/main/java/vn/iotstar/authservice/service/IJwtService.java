package vn.iotstar.authservice.service;


import vn.iotstar.authservice.model.entity.User;

public interface IJwtService {

    String generateToken(User user) ;

    long getJwtExpiration();
}











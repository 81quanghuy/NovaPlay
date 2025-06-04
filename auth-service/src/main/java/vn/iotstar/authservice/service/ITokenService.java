package vn.iotstar.authservice.service;

public interface ITokenService {
    String generateToken(String email);
}

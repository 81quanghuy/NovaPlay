package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.model.entity.Token;
import vn.iotstar.authservice.repository.TokenRepository;
import vn.iotstar.authservice.service.ITokenService;

@Service
@RequiredArgsConstructor
public class TokenService implements ITokenService {
    private final TokenRepository tokenRepository;

    public String generateToken(String email) {
        return null;
    }

    public void save(Token token) {
        tokenRepository.save(token);
    }
}

package vn.iotstar.authservice.service.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.util.RoleName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class JwtServiceImplTest {

    @InjectMocks
    private JwtServiceImpl jwtService;

    private RSAPrivateKey privateKey;
    private User dummyUser;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair pair = keyPairGen.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        ReflectionTestUtils.setField(jwtService, "rsaPrivateKey", privateKey);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L); // 1 hour

        Role role = new Role();
        role.setRoleName(RoleName.USER);

        dummyUser = new User();
        dummyUser.setEmail("test@gmail.com");
        dummyUser.setRoles(Set.of(role));
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        String token = jwtService.generateToken(dummyUser);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_withExpiredToken_returnsFalse() {
        // Generate a token manually that is already expired
        String token = Jwts.builder()
                .setSubject("test@gmail.com")
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void extractRoles_returnsCorrectRoles() {
        String token = jwtService.generateToken(dummyUser);
        List<String> roles = jwtService.extractRoles(token);
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_USER"));
    }
}

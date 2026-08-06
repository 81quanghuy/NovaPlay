package vn.iotstar.authservice.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceImplTest {

    @InjectMocks
    private JwtServiceImpl jwtService;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private User dummyUser;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey  = (RSAPublicKey) pair.getPublic();

        ReflectionTestUtils.setField(jwtService, "rsaPrivateKey", privateKey);
        ReflectionTestUtils.setField(jwtService, "rsaPublicKey",  publicKey);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtService, "issuer",   "novaplay-auth");
        ReflectionTestUtils.setField(jwtService, "audience", "novaplay");
        ReflectionTestUtils.setField(jwtService, "kid",      "v1");

        Role role = new Role();
        role.setRoleName(RoleName.USER);

        dummyUser = new User();
        dummyUser.setId(UUID.randomUUID());
        dummyUser.setEmail("test@gmail.com");
        dummyUser.setRoles(Set.of(role));
    }

    @Test
    void generateToken_containsIssuerAudienceJtiKid() {
        String token = jwtService.generateToken(dummyUser);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(token).getPayload();

        assertEquals("novaplay-auth", claims.getIssuer());
        assertEquals(Set.of("novaplay"), claims.getAudience());
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isEmpty());
    }

    @Test
    void generateToken_jtiIsUniqueEachCall() {
        String jti1 = jwtService.extractJti(jwtService.generateToken(dummyUser));
        String jti2 = jwtService.extractJti(jwtService.generateToken(dummyUser));
        assertNotEquals(jti1, jti2);
    }

    @Test
    void isTokenValid_withValidToken_returnsTrue() {
        String token = jwtService.generateToken(dummyUser);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_withExpiredToken_returnsFalse() {
        String token = Jwts.builder()
                .subject("test@gmail.com")
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_withWrongKey_returnsFalse() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey otherPrivate = (RSAPrivateKey) gen.generateKeyPair().getPrivate();
        String token = Jwts.builder()
                .subject("test@gmail.com")
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(otherPrivate, Jwts.SIG.RS256)
                .compact();
        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void extractEmail_returnsCorrectSubject() {
        String token = jwtService.generateToken(dummyUser);
        assertEquals("test@gmail.com", jwtService.extractEmail(token));
    }

    @Test
    void extractRoles_returnsCorrectRoles() {
        String token = jwtService.generateToken(dummyUser);
        List<String> roles = jwtService.extractRoles(token);
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    void extractJti_returnsNonNullUuid() {
        String token = jwtService.generateToken(dummyUser);
        String jti = jwtService.extractJti(token);
        assertNotNull(jti);
        assertDoesNotThrow(() -> UUID.fromString(jti));
    }

    @Test
    void extractIssuer_returnsNovaplayAuth() {
        String token = jwtService.generateToken(dummyUser);
        assertEquals("novaplay-auth", jwtService.extractIssuer(token));
    }
}

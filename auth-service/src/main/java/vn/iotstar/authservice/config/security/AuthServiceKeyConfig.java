package vn.iotstar.authservice.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@Slf4j
public class AuthServiceKeyConfig {

    @Value("${auth.jwt.kid:v1}")
    private String kid;

    @Bean
    public RSAPrivateKey privateKey() throws Exception {
        log.info("Loading RSA private key...");
        ClassPathResource resource = new ClassPathResource("certs/private.pem");
        try (InputStream inputStream = resource.getInputStream()) {
            String key = new String(inputStream.readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            log.info("RSA private key loaded successfully.");
            return (RSAPrivateKey) kf.generatePrivate(keySpec);
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        log.info("Loading RSA public key...");
        ClassPathResource resource = new ClassPathResource("certs/public.pem");
        try (InputStream inputStream = resource.getInputStream()) {
            String key = new String(inputStream.readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            log.info("RSA public key loaded successfully.");
            return (RSAPublicKey) kf.generatePublic(keySpec);
        }
    }

    public String getKid() {
        return kid;
    }
}

package vn.iotstar.authservice.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
@Slf4j
public class AuthServiceKeyConfig {
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
}

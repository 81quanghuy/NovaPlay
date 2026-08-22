package vn.iotstar.apigateway.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@Slf4j
public class JwtConfig {

    /**
     * Kiểu {@link Resource} chứ không phải {@code ClassPathResource}: prod mount public key từ
     * Secret vào {@code file:/etc/novaplay/keys/public.pem} thay vì bake vào image, còn dev vẫn
     * dùng default classpath. Spring tự chọn đúng loại resource theo tiền tố.
     */
    @Value("${rsa.public.key-location:classpath:certs/public.pem}")
    private Resource publicKeyLocation;

    @Bean
    public RSAPublicKey publicKey() throws Exception {
        log.info("Loading RSA public key from {}", publicKeyLocation);
        try (InputStream inputStream = publicKeyLocation.getInputStream()) {
            String key = new String(inputStream.readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            log.info("RSA public key loaded successfully.");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        }
    }
}

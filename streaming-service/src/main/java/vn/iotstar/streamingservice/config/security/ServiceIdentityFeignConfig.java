package vn.iotstar.streamingservice.config.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Gắn cứng danh tính {@code ROLE_SERVICE} cho lời gọi tới media-service — dùng cho
 * {@code MediaServiceClient} (đọc video-manifest không owner-gate). CỐ Ý KHÔNG mang
 * {@code @Configuration}, cùng lý do với {@link FeignSecurityConfig}: tránh bị áp toàn cục cho
 * mọi Feign client qua component-scan.
 */
public class ServiceIdentityFeignConfig {

    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLES = "X-User-Roles";

    @Bean
    public RequestInterceptor serviceIdentityRelay(
            @Value("${application.security.gateway-secret.value:}") String gatewaySecret) {
        return template -> {
            template.header(X_USER_EMAIL, "system@streaming-service");
            template.header(X_USER_ROLES, "[ROLE_SERVICE]");
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                template.header(GatewayAuthFilter.X_GATEWAY_AUTH, gatewaySecret);
            }
        };
    }
}

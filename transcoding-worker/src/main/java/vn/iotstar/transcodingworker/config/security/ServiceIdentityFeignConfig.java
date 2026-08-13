package vn.iotstar.transcodingworker.config.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * transcoding-worker không xử lý request của người dùng thật — nó gọi media-service như một
 * service-to-service call thuần tuý. Header {@code X-User-Roles: [ROLE_SERVICE]} được gắn CỨNG ở
 * đây, không bao giờ tới từ một người dùng thật hay một JWT thật (xem ghi chú tại media-service's
 * {@code HeaderAuthenticationFilter}). Vẫn phải kèm {@code X-Gateway-Auth} vì service-to-service
 * bỏ qua api-gateway.
 */
@Configuration
public class ServiceIdentityFeignConfig {

    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLES = "X-User-Roles";
    private static final String X_GATEWAY_AUTH = "X-Gateway-Auth";

    @Bean
    public RequestInterceptor serviceIdentityRelay(
            @Value("${application.security.gateway-secret.value:}") String gatewaySecret) {
        return template -> {
            template.header(X_USER_EMAIL, "system@transcoding-worker");
            template.header(X_USER_ROLES, "[ROLE_SERVICE]");
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                template.header(X_GATEWAY_AUTH, gatewaySecret);
            }
        };
    }
}

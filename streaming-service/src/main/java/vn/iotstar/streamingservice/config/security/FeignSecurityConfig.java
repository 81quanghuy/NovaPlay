package vn.iotstar.streamingservice.config.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

/**
 * Chuyển tiếp danh tính người dùng THẬT sang movie-service — dùng cho {@code MovieServiceClient}.
 * <p>
 * CỐ Ý KHÔNG mang {@code @Configuration}: lớp này được truyền qua
 * {@code @FeignClient(configuration = FeignSecurityConfig.class)}, không phải component-scan.
 * Nếu mang {@code @Configuration}, Spring sẽ component-scan nó vào context chính và áp
 * {@code identityHeaderRelay} này cho MỌI Feign client — kể cả {@code MediaServiceClient}, vốn
 * phải luôn mang danh tính {@code ROLE_SERVICE} (xem {@link ServiceIdentityFeignConfig}), không
 * bao giờ là danh tính người dùng thật.
 */
public class FeignSecurityConfig {

    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLES = "X-User-Roles";

    @Bean
    public RequestInterceptor identityHeaderRelay() {
        return template -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String email) {
                template.header(X_USER_EMAIL, email);

                String roles = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","));
                if (!roles.isEmpty()) {
                    template.header(X_USER_ROLES, roles);
                }
            }
        };
    }

    @Bean
    public RequestInterceptor gatewaySecretRelay(
            @Value("${application.security.gateway-secret.value:}") String gatewaySecret) {
        return template -> {
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                template.header(GatewayAuthFilter.X_GATEWAY_AUTH, gatewaySecret);
            }
        };
    }
}

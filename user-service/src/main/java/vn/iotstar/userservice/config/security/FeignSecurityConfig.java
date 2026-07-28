package vn.iotstar.userservice.config.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

@Configuration
public class FeignSecurityConfig {

    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLES = "X-User-Roles";

    /**
     * Chuyển tiếp danh tính người dùng sang lời gọi service khác.
     * <p>
     * Phải gửi kèm cả roles: nếu chỉ có email, service đích sẽ thấy một người dùng đã xác thực
     * nhưng không có quyền nào, khiến mọi kiểm tra phân quyền ở đó đều thất bại.
     */
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

    /**
     * Lời gọi giữa các service không đi qua gateway, nên phải tự mang theo bí mật dùng chung —
     * nếu không service đích sẽ từ chối chúng như thể là truy cập trực tiếp.
     */
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

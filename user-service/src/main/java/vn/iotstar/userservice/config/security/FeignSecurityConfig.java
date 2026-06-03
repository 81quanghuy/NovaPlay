package vn.iotstar.userservice.config.security;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class FeignSecurityConfig {

    private static final String X_USER_EMAIL = "X-User-Email";

    @Bean
    public RequestInterceptor emailHeaderRelay() {
        return template -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String email) {
                template.header(X_USER_EMAIL, email);
            }
        };
    }
}

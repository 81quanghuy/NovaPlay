package vn.iotstar.apigateway.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF as the API is stateless (using JWT)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Configure authorization rules
                .authorizeExchange(exchange -> exchange
                        // Permit all requests at the Spring Security level.
                        // Reason: Authentication is handled entirely by
                        // our custom GatewayFilter (AuthenticationFilter).
                        .anyExchange().permitAll()
                )
                .build();
    }
}
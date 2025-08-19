package vn.iotstar.apigateway.configs;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RouteConfig {

    /**
     * English: Defines the key resolution strategy for rate limiting.
     * It uses the user's email (added by AuthenticationFilter) as the key.
     * For unauthenticated requests, it falls back to the IP address.
     */
    @Bean
    public KeyResolver userEmailOrIpKeyResolver() {
        return exchange -> {
            // Try to get the user email from the header added by AuthenticationFilter.
            String userEmail = exchange.getRequest().getHeaders().getFirst("X-User-Email");
            if (userEmail != null && !userEmail.isEmpty()) {
                return Mono.just(userEmail);
            }
            // Fallback to IP address for anonymous users.
            return Mono.just(Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress());
        };
    }
}
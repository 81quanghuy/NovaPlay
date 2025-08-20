package vn.iotstar.apigateway.configs;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import vn.iotstar.apigateway.constants.RouteConfigItem;
import vn.iotstar.apigateway.filters.AuthenticationFilter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static vn.iotstar.apigateway.constants.GateWayConstants.*;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    private final AuthenticationFilter authFilter;

    /**
     * This class is used to configure routes for the API Gateway.
     * It defines the services and their respective routes,
     * along with filters for rate limiting, circuit breaking, and path rewriting.
     */
    private final Map<String, RouteConfigItem> services = Map.of(
            USER_SERVICE,
            new RouteConfigItem(API_V1 + "users/**", true, true,true),
            MOVIE_SERVICE,
            new RouteConfigItem(API_V1 + "movies/**", true, true,false),
            AUTH_SERVICE,
            new RouteConfigItem(API_V1 + "auth/**", true, true,true)
    );

    /**
     * This method builds the route locator for the API Gateway.
     * It iterates through the configured services and their routes,
     * applying filters for response headers, circuit breakers, rate limiters, and path rewriting.
     *
     * @param builder RouteLocatorBuilder to build the routes
     * @return RouteLocator containing the configured routes
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();

        for (Map.Entry<String, RouteConfigItem> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            RouteConfigItem item = entry.getValue();

            routes.route(serviceName, r -> r
                    .path(item.path())
                    .filters(f -> {
                        // Thêm thời gian phản hồi
                        f.filter(authFilter);

                        // Circuit breaker (ngắt mạch nếu lỗi nhiều)
                        if (item.useCircuitBreaker()) {
                            f.circuitBreaker(config -> config
                                    .setName(serviceName + CIRCUIT_BREAKER)
                                    .setFallbackUri(FALLBACK_URI));
                        }

                        // Retry (thử lại nếu lỗi)
                        if (item.useRetry()) {
                            f.retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(Duration.ofMillis(100),
                                            Duration.ofMillis(1000), 2, true));
                        }

                        // Rate Limiter (giới hạn request)
                        if (item.useRateLimiter()) {
                            f.requestRateLimiter(config -> config
                                    .setRateLimiter(redisRateLimiter())
                                    .setKeyResolver(userEmailOrIpKeyResolver()));
                        }

                        return f;
                    })
                    .uri(LB + serviceName)
            );
        }

        return routes.build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(1, 1, 1);
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10))
                        .build()).build());
    }

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

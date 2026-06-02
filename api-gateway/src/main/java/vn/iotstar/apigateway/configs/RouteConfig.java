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
import vn.iotstar.apigateway.constants.GateWayConstants;
import vn.iotstar.apigateway.constants.RouteConfigItem;
import vn.iotstar.apigateway.filters.AuthenticationFilter;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import static vn.iotstar.apigateway.constants.GateWayConstants.*;

@RequiredArgsConstructor
@Configuration
public class RouteConfig {

    private final AuthenticationFilter authFilter;

    private final Map<String, RouteConfigItem> services = Map.of(
            USER_SERVICE,
            new RouteConfigItem(API_V1 + "users/**", true, true, true),
            MOVIE_SERVICE,
            new RouteConfigItem(API_V1 + "movies/**", true, true, false),
            AUTH_SERVICE,
            new RouteConfigItem(API_V1 + "auth/**", true, true, true)
    );

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();

        for (Map.Entry<String, RouteConfigItem> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            RouteConfigItem item = entry.getValue();

            routes.route(serviceName, r -> r
                    .path(item.path())
                    .filters(f -> {
                        f.filter(authFilter);

                        if (item.useCircuitBreaker()) {
                            f.circuitBreaker(config -> config
                                    .setName(serviceName + CIRCUIT_BREAKER)
                                    .setFallbackUri(FALLBACK_URI));
                        }

                        if (item.useRetry()) {
                            f.retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(Duration.ofMillis(100),
                                            Duration.ofMillis(1000), 2, true));
                        }

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

        for (String serviceName : services.keySet()) {
            String shortName = serviceName.replace("-service", "");
            routes.route(serviceName + "-swagger", r -> r
                    .path("/swagger/" + shortName + "/v3/api-docs")
                    .filters(f -> f
                            .rewritePath("/swagger/" + shortName + "/(?<segment>.*)", "/${segment}")
                    )
                    .uri(LB + serviceName)
            );
        }

        return routes.build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build())
                .build());
    }

    @Bean
    public KeyResolver userEmailOrIpKeyResolver() {
        return exchange -> {
            String userEmail = exchange.getRequest().getHeaders().getFirst(GateWayConstants.X_USER_EMAIL);
            if (userEmail != null && !userEmail.isEmpty()) {
                return Mono.just(userEmail);
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null) {
                return Mono.just("unknown");
            }
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}

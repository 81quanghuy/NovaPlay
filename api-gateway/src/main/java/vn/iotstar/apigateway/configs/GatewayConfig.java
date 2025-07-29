package vn.iotstar.apigateway.configs;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.iotstar.apigateway.filters.ResponseTimeFilter;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                // Route cho user-service
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/users/(?<segment>.*)", "/${segment}")
                                .filter(new ResponseTimeFilter())
                                .circuitBreaker(config -> config.setName("userServiceCircuitBreaker"))
                        )
                        .uri("lb://user-service")
                )

                // Route cho admin (phân quyền ADMIN)
                .route("admin-service", r -> r
                        .path("/api/admin/**")
                        .filters(f -> f
                                .rewritePath("/api/admin/(?<segment>.*)", "/${segment}")
                                .filter(new ResponseTimeFilter())
                        )
                        .uri("lb://admin-service")
                )

                // Route cho movie-service
                .route("movie-service", r -> r
                        .path("/api/movies/**")
                        .filters(f -> f
                                .rewritePath("/api/movies/(?<segment>.*)", "/${segment}")
                                .filter(new ResponseTimeFilter())
                        )
                        .uri("lb://movie-service")
                )

                .build();
    }
}
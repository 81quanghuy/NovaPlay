package vn.iotstar.apigateway.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.iotstar.apigateway.filters.ResponseTimeFilter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static vn.iotstar.apigateway.constants.GateWayContants.*;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    private final ResponseTimeFilter responseTimeFilter;
    private final Map<String, List<String>> services = Map.of(
            USER_SERVICE, pathConfig(List.of("users"))
    );

    private List<String> pathConfig(List<String> paths) {
        return paths.stream()
                .map(path -> API_V1 + path + "/**")
                .collect(Collectors.toList());
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            List<String> servicePath = entry.getValue();
            for (String path : servicePath) {
                routes.route(serviceName, r -> r
                        .path(path)
                        .filters(f -> f
                                .filter(responseTimeFilter)
                                .circuitBreaker(config -> config.setName(serviceName + CIRCUIT_BREAKER)))
                        .uri(LB + serviceName)
                );
            }
        }
        return routes.build();
    }
}

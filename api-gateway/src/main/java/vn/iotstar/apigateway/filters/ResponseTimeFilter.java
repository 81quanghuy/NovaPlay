package vn.iotstar.apigateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static vn.iotstar.apigateway.constants.GateWayContants.X_RESPONSE_TIME;

@Component
public class ResponseTimeFilter implements GatewayFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().getHeaders().add(X_RESPONSE_TIME, Instant.now().toString());
        return chain.filter(exchange);
    }
}
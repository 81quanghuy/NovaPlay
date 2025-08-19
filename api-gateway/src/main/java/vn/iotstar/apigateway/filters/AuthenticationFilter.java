package vn.iotstar.apigateway.filters;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.iotstar.utils.jwt.JwtUtil;

import java.util.List;
import java.util.function.Predicate;

@RefreshScope
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter implements GatewayFilter {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        final List<String> apiEndpoints = List.of("/api/v1/auth/register",
                                                    "/api/v1/auth/login",
                                                    "/api/v1/auth/refresh-token");

        Predicate<ServerHttpRequest> isApiSecured = r -> apiEndpoints.stream()
                .noneMatch(uri -> r.getURI().getPath().contains(uri));

        if (isApiSecured.test(request)) {
            if (!request.getHeaders().containsKey("Authorization")) {
                return this.onError(exchange, "Authorization header is missing in request");
            }

            final String authHeader = request.getHeaders().getOrEmpty("Authorization").getFirst();

            if (!authHeader.startsWith("Bearer ")) {
                return this.onError(exchange, "Authorization header is invalid");
            }

            final String token = authHeader.substring(7);

            try {
                if (Boolean.TRUE.equals(jwtUtil.validateToken(token))) {
                    return this.onError(exchange, "Token is expired or invalid");
                }
            } catch (Exception e) {
                log.error("Error validating token: {}", e.getMessage());
                return this.onError(exchange, "Token validation error");
            }

            this.populateRequestWithHeaders(exchange, token);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        log.error("Unauthorized request: {}", err);
        response.getHeaders().add("Error", err);
        return response.setComplete();
    }

    private void populateRequestWithHeaders(ServerWebExchange exchange, String token) {
        Claims claims = jwtUtil.extractAllClaims(token);
        exchange.getRequest().mutate()
                .header("X-User-Email", claims.getSubject())
                .header("X-User-Roles", String.valueOf(claims.get("roles"))) // Giả sử claim roles tồn tại
                .build();
    }
}
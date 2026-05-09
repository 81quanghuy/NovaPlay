package vn.iotstar.apigateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.iotstar.apigateway.constants.PublicEndpoints;
import vn.iotstar.apigateway.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@RefreshScope
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter implements GatewayFilter {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        Predicate<ServerHttpRequest> isApiSecured = r -> PublicEndpoints.AUTH.stream()
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
                if (Boolean.FALSE.equals(jwtUtil.validateToken(token))) {
                    return this.onError(exchange, "Token is expired or invalid");
                }
            } catch (Exception e) {
                log.error("Error validating token: {}", e.getMessage());
                return this.onError(exchange, "Token validation error");
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String jti = claims.getId();
            if (jti != null) {
                return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti)
                        .flatMap(blacklisted -> {
                            if (Boolean.TRUE.equals(blacklisted)) {
                                return this.onError(exchange, "Token has been revoked");
                            }
                            this.populateRequestWithHeaders(exchange, token);
                            return chain.filter(exchange);
                        });
            }

            this.populateRequestWithHeaders(exchange, token);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorMessage) {
        log.error("API Gateway Authentication Error: {}", errorMessage);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json; charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("path", exchange.getRequest().getPath().toString());
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        errorResponse.put("message", errorMessage);

        try {
            byte[] responseBytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(responseBytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing JSON error response", e);
            return response.setComplete();
        }
    }

    private void populateRequestWithHeaders(ServerWebExchange exchange, String token) {
        Claims claims = jwtUtil.extractAllClaims(token);
        exchange.getRequest().mutate()
                .header("X-User-Email", claims.getSubject())
                .header("X-User-Roles", String.valueOf(claims.get("roles")))
                .build();
    }
}
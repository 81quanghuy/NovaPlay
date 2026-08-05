package vn.iotstar.apigateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.iotstar.apigateway.constants.GateWayConstants;
import vn.iotstar.apigateway.constants.PublicEndpoints;
import vn.iotstar.apigateway.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * Bí mật dùng chung với các service downstream. Để trống thì header sẽ không được gắn —
     * downstream chỉ từ chối request khi chính nó bật kiểm tra.
     */
    @Value("${application.security.gateway-secret:}")
    private String gatewaySecret;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        boolean isPublic = PublicEndpoints.ALL.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isPublic) {
            // Route public không xác thực nên không có claim để gắn, nhưng vẫn phải gỡ header
            // danh tính do client tự đặt — nếu không, chúng sẽ đi thẳng tới downstream.
            return chain.filter(stripClientIdentityHeaders(exchange));
        }

        boolean hasAuthHeader = request.getHeaders().getFirst("Authorization") != null;

        // Đọc catalog là xác thực TUỲ CHỌN, không phải miễn xác thực: không có token thì duyệt
        // với tư cách khách, còn có token thì vẫn phải xác thực và gắn danh tính.
        //
        // Bỏ qua hoàn toàn việc xác thực ở đây sẽ làm hỏng các endpoint quản trị nằm lồng dưới
        // cùng prefix (ví dụ /api/v1/movies/manage): admin gửi kèm token vẫn bị gỡ mất danh tính
        // và luôn nhận 401 từ downstream.
        if (!hasAuthHeader && isPublicGet(request.getMethod().name(), path)) {
            return chain.filter(stripClientIdentityHeaders(exchange));
        }

        if (!hasAuthHeader) {
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
        if (jti == null) {
            return this.onError(exchange, "Token missing jti claim");
        }

        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return this.onError(exchange, "Token has been revoked");
                    }
                    return chain.filter(sanitizeAndEnrich(exchange, claims));
                });
    }

    private boolean isPublicGet(String method, String path) {
        return "GET".equalsIgnoreCase(method)
                && PublicEndpoints.PUBLIC_GET.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Gỡ header danh tính do client tự đặt, nhưng vẫn gắn lại bí mật chứng minh đi qua gateway.
     * <p>
     * Bí mật này trả lời câu hỏi "request có đi qua gateway không", hoàn toàn độc lập với việc
     * người dùng đã đăng nhập hay chưa. Không gắn lại thì mọi request công khai sẽ bị downstream
     * từ chối bằng 403 ngay khi service đó bật kiểm tra gateway-secret ở production.
     */
    private ServerWebExchange stripClientIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(GateWayConstants.X_USER_EMAIL);
                    h.remove(GateWayConstants.X_USER_ROLES);
                    h.remove(GateWayConstants.X_GATEWAY_AUTH);
                });

        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            builder.header(GateWayConstants.X_GATEWAY_AUTH, gatewaySecret);
        }

        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * Gỡ bỏ mọi header danh tính do client tự đặt rồi gắn lại từ claim đã xác thực.
     * Client không được phép tự khai báo mình là ai, kể cả header chứng minh đi qua gateway.
     */
    private ServerWebExchange sanitizeAndEnrich(ServerWebExchange exchange, Claims claims) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(GateWayConstants.X_USER_EMAIL);
                    h.remove(GateWayConstants.X_USER_ROLES);
                    h.remove(GateWayConstants.X_GATEWAY_AUTH);
                })
                .header(GateWayConstants.X_USER_EMAIL, claims.getSubject())
                .header(GateWayConstants.X_USER_ROLES, String.valueOf(claims.get("roles")));

        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            builder.header(GateWayConstants.X_GATEWAY_AUTH, gatewaySecret);
        }

        return exchange.mutate().request(builder.build()).build();
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
}

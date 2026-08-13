package vn.iotstar.streamingservice.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Bắt buộc request phải đi qua API Gateway — bản sao của movie-service's {@code GatewayAuthFilter}
 * (không có shared lib). Áp cho MỌI request kể cả {@code /hls/**} (public-GET ở tầng gateway):
 * filter này chỉ trả lời "request có đi qua gateway không", độc lập với việc có identity thật hay
 * không — bảo vệ nội dung {@code /hls/**} nằm ở playback token riêng, không phải ở đây.
 */
@Component
@Slf4j
public class GatewayAuthFilter extends OncePerRequestFilter {

    static final String X_GATEWAY_AUTH = "X-Gateway-Auth";

    private static final String[] EXEMPT_PATH_PREFIXES = {
            "/actuator/health",
            "/actuator/info"
    };

    private final boolean enabled;
    private final byte[] expectedSecret;
    private final ObjectMapper objectMapper;

    public GatewayAuthFilter(
            @Value("${application.security.gateway-secret.enabled:false}") boolean enabled,
            @Value("${application.security.gateway-secret.value:}") String secret,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.expectedSecret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;

        if (enabled && this.expectedSecret.length == 0) {
            throw new IllegalStateException(
                    "application.security.gateway-secret.enabled=true nhưng chưa cấu hình secret");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : EXEMPT_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(X_GATEWAY_AUTH);

        if (!matchesSecret(presented)) {
            log.warn("Rejected request to {} without a valid gateway signature (remote={})",
                    request.getRequestURI(), request.getRemoteAddr());
            writeForbidden(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesSecret(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        body.put("message", "Direct access is not allowed; requests must go through the API gateway");
        body.put("statusCode", HttpStatus.FORBIDDEN.value());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

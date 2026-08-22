package vn.iotstar.authservice.config.security;

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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Bắt buộc request phải đi qua API Gateway — bản sao của filter cùng tên ở 6 service Maven (không
 * có shared lib), khác ở chỗ dùng Jackson 3 ({@code tools.jackson}) theo baseline Spring Boot 4.1
 * của service này.
 * <p>
 * Lý do ở auth-service KHÁC với các service kia: service này KHÔNG tin header {@code X-User-Email}
 * — nó tự verify JWT — nên đây không phải lớp chống mạo danh. Thứ nó bảo vệ là
 * {@code RequestRateLimiter} của gateway: {@code /login}, {@code /verify-otp},
 * {@code /forgot-password} và {@code /resend-registration-otp} chỉ được chống brute-force và
 * email-bombing ở tầng gateway. Gọi thẳng vào cổng 8000 là bỏ qua toàn bộ lớp đó, nên đường vào
 * trực tiếp phải bị đóng ở production.
 * <p>
 * Tắt mặc định để dev chạy local gọi thẳng service được; PROD bắt buộc phải bật.
 */
@Component
@Slf4j
public class GatewayAuthFilter extends OncePerRequestFilter {

    static final String X_GATEWAY_AUTH = "X-Gateway-Auth";

    /** Endpoint hạ tầng do orchestrator (probe) và Alloy (scrape metric) gọi trực tiếp, không qua gateway. */
    private static final String[] EXEMPT_PATH_PREFIXES = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
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

    /** So sánh trong thời gian hằng định để không rò rỉ secret qua thời gian phản hồi. */
    private boolean matchesSecret(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }

    /**
     * Dựng JSON bằng ObjectNode thay vì serialize trực tiếp một POJO có kiểu java.time: filter này
     * chạy trước chuỗi MVC nên không được phép phụ thuộc vào cấu hình của ObjectMapper được inject.
     * Đây là đường trả lỗi bảo mật — nó phải luôn ghi ra được phản hồi.
     */
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

package vn.iotstar.mediaservice.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthFilter;
    private final GatewayAuthFilter gatewayAuthFilter;
    private final RestAuthenticationHandlers authHandlers;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**",
            // Chỉ probe sức khoẻ mới để mở — orchestrator gọi chúng trước khi có danh tính.
            // /actuator/prometheus và /actuator/metrics lộ tên endpoint nội bộ cùng số liệu
            // vận hành nên phải yêu cầu xác thực.
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * {@code GET /api/v1/media} (admin-list media theo {@code ownerId}, KHÔNG phải
     * {@code /api/v1/media/me} hay {@code /api/v1/media/{id}} — AntPathMatcher không có wildcard
     * nên chỉ khớp chính xác path này). Đây là lớp phòng thủ đầu tiên; lớp thứ hai là
     * {@code @PreAuthorize("hasRole('ADMIN')")} ở {@code MediaController}, giống
     * {@code MovieController}.
     */
    private static final String[] ADMIN_ONLY_ENDPOINTS = {
            "/api/v1/media"
    };

    /**
     * Manifest HLS phải đọc/ghi được bởi streaming-service và transcoding-worker — hai service
     * không phải chủ sở hữu video gốc, nên KHÔNG dùng owner-gate như {@code /api/v1/media/{id}}.
     * {@code ROLE_SERVICE} là authority chỉ tồn tại ở header {@code X-User-Roles} do một service
     * khác tự gắn (qua Feign interceptor) — không bao giờ được mint vào JWT hay seed vào bảng roles
     * của auth-service, xem ghi chú tại {@link HeaderAuthenticationFilter}.
     */
    private static final String[] SERVICE_OR_ADMIN_ENDPOINTS = {
            "/api/v1/media/video-manifests/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, ADMIN_ONLY_ENDPOINTS).hasRole("ADMIN")
                        .requestMatchers(SERVICE_OR_ADMIN_ENDPOINTS).hasAnyRole("ADMIN", "SERVICE")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Không có hai handler này thì request thiếu danh tính nhận 403 thay vì 401.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(authHandlers.accessDeniedHandler())
                )
                // Chứng minh nguồn gốc gateway phải được kiểm tra trước khi tin bất kỳ header danh
                // tính nào. Filter này áp cho cả request công khai: nó trả lời câu hỏi "request có
                // đi qua gateway không", độc lập với việc người dùng đã đăng nhập hay chưa.
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(headerAuthFilter, GatewayAuthFilter.class);
        return http.build();
    }
}

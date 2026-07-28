package vn.iotstar.movieservice.config.security;

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

    /** Catalog đọc được mà không cần đăng nhập, giống trang chủ của một dịch vụ xem phim. */
    private static final String[] PUBLIC_READ_ENDPOINTS = {
            "/api/v1/movies/**",
            "/api/v1/genres/**",
            "/api/v1/artists/**"
    };

    /** Endpoint quản trị nằm lồng dưới đường dẫn công khai nên phải loại trừ tường minh. */
    private static final String[] ADMIN_ONLY_ENDPOINTS = {
            "/api/v1/movies/manage",
            "/api/v1/movies/manage/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Phải đứng TRƯỚC matcher công khai bên dưới: luật đầu tiên khớp sẽ thắng,
                        // mà /api/v1/movies/manage cũng nằm trong /api/v1/movies/**. Không có dòng
                        // này thì khách ẩn danh nhận 403 từ @PreAuthorize thay vì 401, và danh sách
                        // endpoint quản trị chỉ còn đúng một lớp bảo vệ.
                        .requestMatchers(ADMIN_ONLY_ENDPOINTS).authenticated()
                        // Chỉ GET mới công khai. Mọi method ghi đều rơi xuống anyRequest() bên dưới
                        // và còn phải qua @PreAuthorize("hasRole('ADMIN')") ở tầng controller.
                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_ENDPOINTS).permitAll()
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

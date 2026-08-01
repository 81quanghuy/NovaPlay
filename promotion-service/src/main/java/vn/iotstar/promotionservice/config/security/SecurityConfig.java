package vn.iotstar.promotionservice.config.security;

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

    private static final String COUPONS_BASE = "/api/v1/promotions/coupons";
    /** Khớp đúng một segment id, ví dụ {@code /coupons/{id}} — KHÔNG khớp
     *  {@code /coupons/{code}/redeem} hay {@code /coupons/{code}/validate} (hai segment). */
    private static final String COUPON_BY_ID = "/api/v1/promotions/coupons/*";
    private static final String REDEMPTION_CANCEL = "/api/v1/promotions/redemptions/*/cancel";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Tạo/cập nhật/xoá coupon là thao tác quản trị. Dùng matcher theo HTTP
                        // method + wildcard MỘT segment (không phải /coupons/**) để không vô tình
                        // khoá luôn POST .../redeem hay GET .../validate, hai endpoint có nhiều
                        // hơn một segment phía sau /coupons.
                        .requestMatchers(HttpMethod.POST, COUPONS_BASE).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, COUPON_BY_ID).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, COUPON_BY_ID).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, REDEMPTION_CANCEL).hasRole("ADMIN")
                        // GET /coupons (danh sách, cần ADMIN) và GET /coupons/{code}/validate
                        // (public-authenticated) CHIA SẺ cùng prefix nên không phân biệt được ở
                        // tầng filter theo path — lớp thứ hai (@PreAuthorize trên
                        // CouponController#list) enforce ADMIN riêng cho danh sách. Ở đây cả hai
                        // chỉ cần authenticated().
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

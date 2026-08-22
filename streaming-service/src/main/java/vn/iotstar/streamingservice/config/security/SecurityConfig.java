package vn.iotstar.streamingservice.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // Alloy scrape thẳng vào pod, không qua gateway nên không có danh tính.
            // api-gateway không route /actuator/** ra ngoài, path này chỉ tới được từ trong cụm.
            "/actuator/prometheus",
            /*
             * KHÔNG có identity thật ở đây theo thiết kế — trình phát HLS (đặc biệt engine HLS gốc
             * của Safari/iOS) không tự gắn được header Authorization tuỳ ý vào từng request tải
             * segment. Bảo vệ nội dung nằm ở playback token ngắn hạn (query param "pt", validate
             * trong StreamingHlsController), KHÔNG phải ở tầng Spring Security. api-gateway cũng
             * coi path này là public-GET tương ứng (xem PublicEndpoints.PUBLIC_GET) — GatewayAuthFilter
             * vẫn bắt buộc chứng minh request đi qua gateway như mọi path khác.
             */
            "/api/v1/streaming/hls/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(authHandlers.accessDeniedHandler())
                )
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(headerAuthFilter, GatewayAuthFilter.class);
        return http.build();
    }
}

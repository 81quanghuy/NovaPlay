package vn.iotstar.userservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Bật tính năng bảo mật ở cấp độ phương thức (@PreAuthorize)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Vô hiệu hóa CSRF vì chúng ta dùng JWT (stateless)
        http.csrf(AbstractHttpConfigurer::disable);

        // Cấu hình OAuth2 Resource Server để xác thực JWT
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            // Sử dụng Role Converter đã tạo để Spring Security hiểu được roles từ Keycloak
            JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
            jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
        }));

        http.authorizeHttpRequests(auth -> {
            auth.anyRequest().authenticated();
        });

        return http.build();
    }
}
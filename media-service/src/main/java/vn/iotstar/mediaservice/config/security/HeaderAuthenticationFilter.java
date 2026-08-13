package vn.iotstar.mediaservice.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Không phân biệt nguồn gốc của các role trong header: {@code ROLE_SERVICE} (dùng cho gọi
 * service-to-service từ streaming-service/transcoding-worker) đi qua đúng con đường này, nhưng nó
 * KHÔNG BAO GIỜ đến từ auth-service — không có trong {@code RoleName} enum, không được mint vào
 * JWT, không seed vào bảng {@code roles}. Nó chỉ tồn tại ở header {@code X-User-Roles} do caller
 * tự gắn cứng qua một Feign {@code RequestInterceptor} riêng (khác với interceptor relay danh
 * tính người dùng thật), vẫn phải qua {@link GatewayAuthFilter} như mọi request khác.
 */
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String X_USER_EMAIL = "X-User-Email";
    private static final String X_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String email = request.getHeader(X_USER_EMAIL);

        if (email != null && !email.isBlank()) {
            String rolesHeader = request.getHeader(X_USER_ROLES);
            List<GrantedAuthority> authorities = parseRoles(rolesHeader);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }
        // Gateway sends roles as: [ROLE_USER] or [ROLE_USER, ROLE_ADMIN]
        String cleaned = rolesHeader.replaceAll("[\\[\\]\\s]", "");
        return Arrays.stream(cleaned.split(","))
                .filter(s -> !s.isEmpty())
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}

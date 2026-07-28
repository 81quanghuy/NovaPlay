package vn.iotstar.authservice.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.iotstar.authservice.service.JwtService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Nếu không có token, cho qua để các filter khác xử lý
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            final String userEmail = jwtService.extractEmail(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(jwt)) {
                    // Stateless parsing: Build authorities from token directly
                    java.util.List<String> roles = jwtService.extractRoles(jwt);
                    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities =
                            roles == null ? java.util.Collections.emptyList() :
                                    roles.stream()
                                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                            .toList();

                    // Create partial User to act as the Principal without hitting DB
                    vn.iotstar.authservice.model.entity.User userDetails = new vn.iotstar.authservice.model.entity.User();
                    userDetails.setEmail(userEmail);
                    userDetails.setId(java.util.UUID.randomUUID());
                    userDetails.setUsername(userEmail);
                    userDetails.setIsEmailVerified(true);
                    userDetails.setIsActive(true);
                    java.util.Set<vn.iotstar.authservice.model.entity.Role> rolesSet = new java.util.HashSet<>();
                    if (roles != null) {
                        for (String roleStr : roles) {
                            vn.iotstar.authservice.model.entity.Role role = new vn.iotstar.authservice.model.entity.Role();
                            role.setRoleName(vn.iotstar.authservice.util.RoleName.valueOf(roleStr.replace("ROLE_", "")));
                            rolesSet.add(role);
                        }
                    }
                    userDetails.setRoles(rolesSet);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid JWT token, do nothing and let it hit unauthorized if it reaches secured endpoints
        }
        filterChain.doFilter(request, response);
    }
}
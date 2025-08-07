package vn.iotstar.apigateway.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

import static vn.iotstar.apigateway.constants.GateWayContants.*;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final Map<String, SecurityConfigItem> services = Map.of(
            USER_SERVICE,
            new SecurityConfigItem(API_V1 + "users/**", true, true, true),
            MOVIE_SERVICE,
            new SecurityConfigItem(API_V1 + "movies/**", true, true, false)
    );

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(auth -> {
            services.forEach((serviceName, config) -> {
                String path = config.getPathPattern();

                if (!config.isAuthRequired()) {
                    auth.pathMatchers(path).permitAll();
                } else {
                    if (config.isAdminAllowed()) {
                        auth.pathMatchers(path.replace("**", "admin/**")).hasRole("ADMIN");
                    }
                    if (config.isUserAllowed()) {
                        auth.pathMatchers(path).hasRole("USER");
                    }
                }
            });

            auth.anyExchange().authenticated();
        });

        http.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
        );

        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter
                (new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

}

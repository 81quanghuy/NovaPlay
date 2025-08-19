//package vn.iotstar.apigateway.configs;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
//import org.springframework.security.config.web.server.ServerHttpSecurity;
//import org.springframework.security.web.server.SecurityWebFilterChain;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.reactive.CorsConfigurationSource;
//import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
//
//import java.util.Arrays;
//import java.util.List;
//
//@Configuration
//@EnableWebFluxSecurity
//public class SecurityConfigOld {
//
//    // Load the list of allowed origins from the application.properties file
//    @Value("${application.cors.allowed-origins}")
//    private List<String> allowedOrigins;
//
//    @Bean
//    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
//        return http
//                // Disable CSRF as the API is stateless (using JWT)
//                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//
//                // Configure CORS using the `corsConfigurationSource` bean
//                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//
//                // Configure authorization rules
//                .authorizeExchange(exchange -> exchange
//                        // Permit all requests at the Spring Security level.
//                        // Reason: Authentication is handled entirely by
//                        // our custom GatewayFilter (AuthenticationFilter).
//                        .anyExchange().permitAll()
//                )
//                .build();
//    }
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//
//        // Allow sending credentials (e.g., cookies, authorization headers)
//        configuration.setAllowCredentials(true);
//
//        // Set the allowed origins from the configuration file
//        configuration.setAllowedOrigins(allowedOrigins);
//
//        // Restrict allowed HTTP methods for enhanced security
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
//
//        // Restrict allowed headers
//        configuration.setAllowedHeaders(Arrays.asList(
//                "Authorization",
//                "Content-Type",
//                "Accept",
//                "X-Requested-With",
//                "Origin",
//                "Access-Control-Request-Method",
//                "Access-Control-Request-Headers"
//        ));
//
//        // Allow the client to read these headers from the response
//        configuration.setExposedHeaders(Arrays.asList(
//                "Authorization",
//                "Content-Type"
//        ));
//
//        // The cache duration for pre-flight requests (in seconds)
//        configuration.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }
//}
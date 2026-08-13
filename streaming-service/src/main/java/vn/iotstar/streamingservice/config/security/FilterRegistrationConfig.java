package vn.iotstar.streamingservice.config.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> disableGatewayAuthFilterAutoRegistration(
            GatewayAuthFilter filter) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HeaderAuthenticationFilter> disableHeaderAuthFilterAutoRegistration(
            HeaderAuthenticationFilter filter) {
        FilterRegistrationBean<HeaderAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

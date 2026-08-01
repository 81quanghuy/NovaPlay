package vn.iotstar.mediaservice.config.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ngăn Boot tự đăng ký các security filter vào servlet chain.
 * <p>
 * Filter khai báo bằng {@code @Component} mặc định được đăng ký vào servlet container, nên
 * chúng sẽ chạy thêm một lần nữa bên ngoài chuỗi của Spring Security — với thứ tự khác và
 * áp cho cả những đường dẫn mà {@code SecurityConfig} đã cho phép công khai. Vị trí duy nhất
 * hai filter này được phép chạy là bên trong chuỗi filter của Spring Security.
 */
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

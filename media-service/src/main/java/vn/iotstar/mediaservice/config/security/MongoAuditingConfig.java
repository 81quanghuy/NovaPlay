package vn.iotstar.mediaservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * {@code @EnableMongoAuditing} nằm ở đây chứ không phải trên lớp application: đặt trên lớp
 * application sẽ đăng ký {@code mongoAuditingHandler} cho MỌI context, kể cả slice test như
 * {@code @WebMvcTest} vốn không có {@code mongoMappingContext}, khiến chúng không khởi động nổi.
 */
@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorAware")
public class MongoAuditingConfig {

    /** Ghi cho những thao tác không có người dùng: job khởi động, seed dữ liệu, batch. */
    private static final String SYSTEM_AUDITOR = "system";

    /**
     * Danh tính người dùng đến từ header do gateway inject (xem {@link HeaderAuthenticationFilter}),
     * nên principal ở đây là một chuỗi email chứ không phải JWT.
     * <p>
     * Với endpoint đọc công khai sẽ không có authentication nào; những request đó không ghi dữ
     * liệu nên giá trị fallback không bao giờ đi vào document.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(name -> !name.isBlank())
                .or(() -> Optional.of(SYSTEM_AUDITOR));
    }
}

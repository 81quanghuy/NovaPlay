package vn.iotstar.promotionservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * {@code @EnableJpaAuditing} nằm ở đây chứ không phải trên lớp application: đặt trên lớp
 * application sẽ đăng ký auditing handler cho MỌI context, kể cả slice test như
 * {@code @WebMvcTest} vốn không có {@code EntityManagerFactory}, khiến chúng không khởi động nổi
 * — cùng vấn đề đã gặp ở {@code MongoAuditingConfig} của movie-service/media-service.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** Ghi cho những thao tác không có người dùng: job khởi động, seed dữ liệu, batch. */
    private static final String SYSTEM_AUDITOR = "system";

    /**
     * Danh tính người dùng đến từ header do gateway inject (xem {@link HeaderAuthenticationFilter}),
     * nên principal ở đây là một chuỗi email chứ không phải JWT. Vì vậy bean {@code auditAwareImpl}
     * của {@code utils} (đọc claim JWT) bị loại khỏi component scan trong
     * {@code PromotionServiceApplication} — chỉ còn đúng bean này.
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

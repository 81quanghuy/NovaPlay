package vn.iotstar.emailservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class MongoAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of("SYSTEM"); // hoặc lấy từ SecurityContext nếu có
    }
}
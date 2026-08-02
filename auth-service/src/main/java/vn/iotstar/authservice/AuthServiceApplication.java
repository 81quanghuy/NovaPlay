package vn.iotstar.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.iotstar.outbox.OutboxConfiguration;

@SpringBootApplication
@EnableFeignClients
// Giữ @EnableScheduling: TokenServiceImpl#purgeExpiredTokens vẫn là @Scheduled. Outbox thì không
// còn tác vụ định kỳ nào — nó chạy theo notification của Postgres.
@EnableScheduling
@Import(OutboxConfiguration.class)
@ComponentScan(basePackages = {
        "vn.iotstar.authservice",  // package chính
        "vn.iotstar.utils", // package Utils,
})
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}

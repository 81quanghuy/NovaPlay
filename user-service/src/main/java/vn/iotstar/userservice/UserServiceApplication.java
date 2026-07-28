package vn.iotstar.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.iotstar.utils.audit.AuditAwareImpl;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@ComponentScan(basePackages = {
        "vn.iotstar.userservice",  // package chính
        "vn.iotstar.utils", // package Utils,
}, excludeFilters = @ComponentScan.Filter(
        // AuditAwareImpl của utils đọc danh tính từ claim JWT, còn service này lấy danh tính từ
        // header do gateway inject. Loại nó ra để chỉ còn đúng một bean AuditorAware.
        type = FilterType.ASSIGNABLE_TYPE, classes = AuditAwareImpl.class))
@EnableMongoAuditing(
        auditorAwareRef = "auditorAware"
)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

package vn.iotstar.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import vn.iotstar.utils.audit.AuditAwareImpl;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(
        // Quét utils để lấy GlobalExceptionHandler dùng chung.
        basePackages = {"vn.iotstar.notificationservice", "vn.iotstar.utils"},
        excludeFilters = {
                // AuditAwareImpl đọc danh tính từ claim của JWT. Service này không bao giờ thấy
                // JWT — gateway đã xác thực và chỉ chuyển xuống header X-User-Email — nên bean đó
                // luôn trả về rỗng và chỉ gây nhầm lẫn.
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuditAwareImpl.class),
                // Hai filter dưới đây được @SpringBootApplication thêm ngầm, nhưng khai báo
                // @ComponentScan tường minh sẽ ghi đè mất chúng. Thiếu chúng thì các slice test
                // (@WebMvcTest, @DataMongoTest) kéo theo cả ApplicationRunner lẫn auto-configuration
                // của service và không khởi động được.
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        })
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

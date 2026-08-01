package vn.iotstar.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import vn.iotstar.utils.audit.AuditAwareImpl;

@SpringBootApplication
@ComponentScan(basePackages = {
        "vn.iotstar.mediaservice",  // package chính
        "vn.iotstar.utils", // package Utils,
}, excludeFilters = {
        // AuditAwareImpl của utils đọc danh tính từ claim JWT, còn service này lấy danh tính từ
        // header do gateway inject. Loại ra để chỉ còn đúng một bean AuditorAware (xem
        // MongoAuditingConfig.auditorAware()) — khớp chính xác với MovieServiceApplication.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuditAwareImpl.class),
        // Hai filter dưới đây là mặc định của @SpringBootApplication, nhưng khai báo @ComponentScan
        // tường minh sẽ ghi đè và làm mất chúng. Thiếu TypeExcludeFilter thì các slice test
        // (@WebMvcTest, @DataMongoTest) không lọc được bean và sẽ kéo cả PendingMediaCleanupJob/
        // MongoAuditingConfig vào, khiến context không khởi tạo nổi vì thiếu MediaRepository/
        // MongoTemplate — xem cùng vấn đề đã gặp ở MovieServiceApplication.
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }

}

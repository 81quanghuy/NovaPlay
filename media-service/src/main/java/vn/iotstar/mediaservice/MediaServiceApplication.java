package vn.iotstar.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// Không còn @ComponentScan tường minh: mọi bean dùng chung đã nằm trong vn.iotstar.mediaservice,
// nên package mặc định của @SpringBootApplication là đủ. Nhờ vậy hai filter TypeExcludeFilter và
// AutoConfigurationExcludeFilter được Boot tự thêm lại — trước đây phải khai báo tay vì
// @ComponentScan tường minh ghi đè mất chúng và làm các slice test (@WebMvcTest, @DataMongoTest)
// kéo cả PendingMediaCleanupJob/MongoAuditingConfig vào context.
@SpringBootApplication
@EnableFeignClients
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }

}

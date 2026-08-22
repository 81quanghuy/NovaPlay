package vn.iotstar.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// Không còn @ComponentScan tường minh: mọi bean dùng chung đã nằm trong vn.iotstar.mediaservice,
// nên package mặc định của @SpringBootApplication là đủ. Nhờ vậy hai filter TypeExcludeFilter và
// AutoConfigurationExcludeFilter được Boot tự thêm lại — trước đây phải khai báo tay vì
// @ComponentScan tường minh ghi đè mất chúng và làm các slice test (@WebMvcTest, @DataMongoTest)
// kéo cả PendingMediaCleanupJob/MongoAuditingConfig vào context.
// @EnableScheduling BẮT BUỘC phải có: thiếu nó thì hai @Scheduled trong PendingMediaCleanupJob
// không bao giờ chạy — Spring vẫn tạo bean, không cảnh báo gì, job đơn giản là im lặng. Hệ quả là
// mất toàn bộ lưới an toàn của pipeline video: upload mồ côi không được đối soát, và manifest kẹt
// ở PROCESSING (do pod worker chết sau khi Kafka đã ack) nằm kẹt vĩnh viễn.
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }

}

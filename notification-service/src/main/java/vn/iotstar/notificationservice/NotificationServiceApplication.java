package vn.iotstar.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Không còn @ComponentScan tường minh: mọi bean dùng chung đã nằm trong
// vn.iotstar.notificationservice, nên package mặc định của @SpringBootApplication là đủ. Nhờ vậy
// hai filter TypeExcludeFilter và AutoConfigurationExcludeFilter được Boot tự thêm lại — trước đây
// phải khai báo tay vì @ComponentScan tường minh ghi đè mất chúng và làm các slice test
// (@WebMvcTest, @DataMongoTest) kéo cả ApplicationRunner lẫn auto-configuration của service vào.
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

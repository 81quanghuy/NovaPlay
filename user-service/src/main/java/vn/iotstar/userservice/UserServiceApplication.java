package vn.iotstar.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// Không còn @ComponentScan tường minh: mọi bean dùng chung đã nằm trong vn.iotstar.userservice,
// nên package mặc định của @SpringBootApplication là đủ.
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableMongoAuditing(
        auditorAwareRef = "auditorAware"
)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

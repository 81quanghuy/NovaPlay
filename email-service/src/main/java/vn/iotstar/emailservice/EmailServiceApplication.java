package vn.iotstar.emailservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {
        "vn.iotstar.emailservice",
        "vn.iotstar.utils",
})
@EnableMongoAuditing(
        auditorAwareRef = "auditorAware"
)
public class EmailServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailServiceApplication.class, args);
    }

}

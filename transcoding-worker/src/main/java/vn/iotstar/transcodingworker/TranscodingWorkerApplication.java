package vn.iotstar.transcodingworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TranscodingWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranscodingWorkerApplication.class, args);
    }

}

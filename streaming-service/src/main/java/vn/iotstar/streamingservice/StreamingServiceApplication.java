package vn.iotstar.streamingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class StreamingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }

}

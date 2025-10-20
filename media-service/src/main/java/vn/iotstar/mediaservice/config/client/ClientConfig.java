package vn.iotstar.mediaservice.config.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplateBean() {
        return new RestTemplate();
    }
}

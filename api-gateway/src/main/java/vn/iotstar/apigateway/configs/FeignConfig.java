package vn.iotstar.apigateway.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FeignConfig {
    @Bean
    public Decoder feignDecoder() {
        ObjectFactory<HttpMessageConverters> objectFactory = () -> new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(customObjectMapper()));
        return new ResponseEntityDecoder(new SpringDecoder(objectFactory));
    }

    @Bean
    public ObjectMapper customObjectMapper() {
        return new ObjectMapper();
    }

	@Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

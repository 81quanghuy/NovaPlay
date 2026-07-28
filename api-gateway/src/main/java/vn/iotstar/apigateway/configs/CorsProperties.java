package vn.iotstar.apigateway.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "application.cors")
@Component
@Getter
@Setter
public class CorsProperties {

    private List<String> allowedOrigins;
}

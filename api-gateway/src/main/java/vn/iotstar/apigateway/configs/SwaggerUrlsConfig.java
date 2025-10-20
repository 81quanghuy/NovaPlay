package vn.iotstar.apigateway.configs;

import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class SwaggerUrlsConfig {

    @Bean
    @Lazy(false)
    public SwaggerUiConfigParameters swaggerUiConfigParameters(
            SwaggerUiConfigProperties props,
            DiscoveryClient discovery) {

        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = new HashSet<>();

        discovery.getServices().stream()
                // <-- SỬA LẠI DÒNG NÀY, thêm .toLowerCase()
                .filter(svc -> svc.toLowerCase().endsWith("-service"))
                .forEach(svc -> {
                    String shortName = svc.toLowerCase().replace("-service", "");
                    String swaggerUrl = "/swagger/" + shortName + "/v3/api-docs";

                    AbstractSwaggerUiConfigProperties.SwaggerUrl swaggerUrlObj = new AbstractSwaggerUiConfigProperties.SwaggerUrl();
                    swaggerUrlObj.setName(shortName);
                    swaggerUrlObj.setUrl(swaggerUrl);

                    urls.add(swaggerUrlObj);
                });

        props.setUrls(urls);
        return new SwaggerUiConfigParameters(props);
    }
}
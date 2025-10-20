package vn.iotstar.apigateway.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FallbackController {

    private final SwaggerUiConfigParameters swaggerUiConfigParameters;

    @GetMapping("/fallback/message")
    public Mono<String> fallbackMessage() {
        return Mono.just("Service is temporarily unavailable. Please try again later.");
    }
    @GetMapping("/debug-swagger-urls")
    public ResponseEntity<Set<String>> getSwaggerUrls() {
        // Lấy ra danh sách các URL đã được cấu hình và trả về
        Set<String> urls = swaggerUiConfigParameters.getUrls().stream()
                .map(AbstractSwaggerUiConfigProperties.SwaggerUrl::getUrl)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(urls);
    }
}
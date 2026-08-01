package vn.iotstar.mediaservice.config.client;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition
public class OpenApiConfig {

    // Server URLs, configurable via application.yml
    @Value("${openapi.server-url.local:http://localhost:8081}")
    private String localServer;

    @Value("${openapi.server-url.dev:https://dev.api.novaplay.vn/media-service}")
    private String devServer;

    @Value("${openapi.server-url.gateway:http://localhost:8072/api/v1/media}")
    private String gatewayServer;

    @Bean
    public OpenAPI openAPI() {

        // Defines the security scheme for Bearer JWT authentication.
        SecurityScheme bearerJwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Provide the Access Token (JWT) issued by this service's /login endpoint.");

        return new OpenAPI()
                .info(new Info()
                        .title("NovaPlay - Media Service API")
                        .description("""
                                API sinh presigned URL để client upload media trực tiếp lên S3, theo dõi
                                trạng thái xử lý upload (PENDING/COMPLETED/FAILED), và trả về CDN URL sau
                                khi upload hoàn tất.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NovaPlay Team")
                                .email("support@novaplay.vn")
                                .url("https://novaplay.vn"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Developer Portal")
                        .url("https://docs.novaplay.vn"))
                .addServersItem(new Server().url(localServer).description("Local Environment"))
                .addServersItem(new Server().url(devServer).description("Development Environment"))
                .addServersItem(new Server().url(gatewayServer).description("Via API Gateway"))
                .components(new Components()
                        // Register the 'bearer-jwt' security scheme.
                        .addSecuritySchemes("bearer-jwt", bearerJwt))
                // Apply the 'bearer-jwt' security scheme to all endpoints by default.
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    /**
     * Groups all media endpoints.
     */
    @Bean
    public GroupedOpenApi mediaApi() {
        return GroupedOpenApi.builder()
                .group("media")
                .pathsToMatch("/api/v1/media/**")
                .build();
    }

    /**
     * Adjusts OpenAPI to add global headers for all operations.
     */
    @Bean
    public OpenApiCustomizer globalHeadersCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.addParametersItem(new Parameter()
                            .in("header").name("X-Request-Id")
                            .description("Request ID for tracing purposes")
                            .required(false)
                            .schema(new StringSchema().maxLength(64)));
                    operation.addParametersItem(new Parameter()
                            .in("header").name("X-Correlation-Id")
                            .description("Correlation ID for distributed tracing")
                            .required(false)
                            .schema(new StringSchema().maxLength(64)));
                })
        );
    }
}

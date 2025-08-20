package vn.iotstar.authservice.config.client;

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

    // English: Server URLs, configurable via application.yml
    // Tiếng Việt: Các URL của server, có thể override qua application.yml
    @Value("${openapi.server-url.local:http://localhost:8081}")
    private String localServer;

    @Value("${openapi.server-url.dev:https://dev.api.novaplay.vn/auth-service}")
    private String devServer;

    @Value("${openapi.server-url.gateway:http://localhost:8072/api/v1/auth}")
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
                        .title("NovaPlay - Authentication Service API")
                        .description("""
                                API chịu trách nhiệm Đăng ký, Đăng nhập, và Quản lý Token (cấp mới, làm mới).
                                Dịch vụ này cấp phát JWT cho các dịch vụ khác trong hệ thống sử dụng.
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
     * English: Groups all authentication-related endpoints.
     * Tiếng Việt: Gom nhóm tất cả các endpoint liên quan đến xác thực.
     */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1. Authentication")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    /**
     * (Optional) Group for user management endpoints if they exist in this service.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("2. User Management (Admin)")
                // Example path, adjust if you have admin endpoints here.
                .pathsToMatch("/api/v1/users/admin/**")
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
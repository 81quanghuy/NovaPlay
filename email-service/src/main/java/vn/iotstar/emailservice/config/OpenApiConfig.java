package vn.iotstar.emailservice.config;

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
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI emailServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Email Service API")
                        .description("APIs for sending emails, OTP lifecycle, and templates.")
                        .version("v1")
                        .contact(new Contact()
                                .name("NovaPlay Team")
                                .email("support@novaplay.io.vn"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("OpenAPI/Swagger Docs")
                        .url("https://swagger.io/docs/"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.novaplay.io.vn").description("Prod") // chỉnh theo thực tế
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * Nhóm endpoint cho Email-service. Điều chỉnh patterns đúng với controller của bạn.
     * Ví dụ:
     *  - /api/v1/emails/** : gửi mail, lịch sử mail
     *  - /api/v1/otp/**    : tạo/verify OTP
     */
    @Bean
    public GroupedOpenApi emailApi(OperationCustomizer tracingHeadersCustomizer) {
        return GroupedOpenApi.builder()
                .group("email-service")
                .packagesToScan("vn.iotstar.emailservice.controller")
                .pathsToMatch("/api/v1/emails/**")
                .addOperationCustomizer(tracingHeadersCustomizer)
                .build();
    }

    /**
     * Thêm global headers cho mọi operation:
     *  - X-Request-Id: ID request phục vụ tracing
     *  - X-Correlation-Id: ID tương quan giữa nhiều service
     */
    @Bean
    public OperationCustomizer tracingHeadersCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Request-Id")
                            .description("Request ID for tracing purposes")
                            .required(false)
                            .schema(new StringSchema().maxLength(64))
            );
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Correlation-Id")
                            .description("Correlation ID for distributed tracing")
                            .required(false)
                            .schema(new StringSchema().maxLength(64))
            );
            return operation;
        };
    }
}

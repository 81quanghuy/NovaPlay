package vn.iotstar.userservice.config.client;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
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

    // ---- Server URLs (override qua application.yml) ----
    @Value("${openapi.server-url.local:http://localhost:8700}")
    private String localServer;

    @Value("${openapi.server-url.dev:https://dev.api.novaplay.vn/user-service}")
    private String devServer;

    // Nếu muốn test trực tiếp qua API Gateway, để thêm server này
    @Value("${openapi.server-url.gateway:http://localhost:8088/api/v1/users}")
    private String gatewayServer;

    // ---- Keycloak (OAuth2) ----
    // URL realm, ví dụ: http://localhost:8080/realms/phim-online-realm
    @Value("${openapi.oauth2.realm-url:http://localhost:8080/realms/phim-online-realm}")
    private String realmUrl;

    // client-id dùng cho Swagger UI (public client, bật PKCE)
    @Value("${openapi.oauth2.client-id:novaplay-frontend}")
    private String swaggerClientId;

    @Bean
    public OpenAPI openAPI() {
        // Xây auth/token URLs từ realmUrl
        String authorizationUrl = realmUrl + "/protocol/openid-connect/auth";
        String tokenUrl = realmUrl + "/protocol/openid-connect/token";

        // --- Security Schemes ---
        SecurityScheme bearerJwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Cung cấp Access Token (JWT) do Keycloak ký");

        SecurityScheme oauth2 = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Đăng nhập qua Keycloak (Authorization Code + PKCE)")
                .flows(new OAuthFlows().authorizationCode(
                        new OAuthFlow()
                                .authorizationUrl(authorizationUrl)
                                .tokenUrl(tokenUrl)
                                .scopes(new Scopes()
                                        .addString("openid", "OpenID scope")
                                        .addString("profile", "Basic profile")
                                        .addString("email", "Email")
                                )
                ));

        // --- Build OpenAPI ---
        return new OpenAPI()
                .info(new Info()
                        .title("NovaPlay - User Service API")
                        .description("""
                                API quản lý hồ sơ người dùng, sở thích và tiến độ xem.\s
                                Xác thực qua Keycloak (Bearer JWT / OAuth2).
                               \s""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NovaPlay Team")
                                .email("support@novaplay.vn")
                                .url("https://novaplay.vn"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Developer Portal")
                        .url("https://docs.novaplay.vn"))
                .addServersItem(new Server().url(localServer).description("Local"))
                .addServersItem(new Server().url(devServer).description("Development"))
                .addServersItem(new Server().url(gatewayServer).description("Via API Gateway"))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", bearerJwt)
                        .addSecuritySchemes("oauth2", oauth2))
                // Áp security mặc định cho toàn bộ operation (có thể override ở @Operation)
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    /**
     * Only for public endpoints, e.g. /api/v1/me/**, /api/v1/profiles/**.
     * @return GroupedOpenApi for public APIs
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch(
                        "/api/**/me/**",
                        "/api/**/profiles/**",
                        "/api/**/favorites/**",
                        "/api/**/watch-progress/**"
                )
                .build();
    }

    /**
     * Only for admin endpoints, e.g. /api/v1/users/admin/**.
     * @return GroupedOpenApi for admin APIs
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/**/admin/**")
                .build();
    }

    /**
     * Adjusts OpenAPI to add global headers for all operations.
     * This is useful for tracing and correlation IDs.
     * @return OpenApiCustomizer to add headers
     */
    @Bean
    public OpenApiCustomizer globalHeadersCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.addParametersItem(new Parameter()
                            .in("header").name("X-Request-Id")
                            .description("Request ID cho mục đích trace")
                            .required(false)
                            .schema(new StringSchema().maxLength(64)));
                    operation.addParametersItem(new Parameter()
                            .in("header").name("X-Correlation-Id")
                            .description("Correlation ID cho distributed tracing")
                            .required(false)
                            .schema(new StringSchema().maxLength(64)));
                })
        );
    }

}

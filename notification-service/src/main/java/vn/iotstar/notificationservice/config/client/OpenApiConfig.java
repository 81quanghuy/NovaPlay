package vn.iotstar.notificationservice.config.client;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("NovaPlay notification-service")
                .version("v1")
                .description("""
                        Gửi thông báo đa kênh (email, in-app) và cho phép người dùng đọc thông báo
                        của chính mình.

                        Không có endpoint đọc công khai nào. Danh tính lấy từ header X-User-Email
                        do API gateway inject sau khi đã xác thực JWT. Gọi thẳng service mà không
                        qua gateway sẽ bị từ chối ở môi trường production.
                        """));
    }
}

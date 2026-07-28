package vn.iotstar.movieservice.config.client;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI movieServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("NovaPlay movie-service")
                .version("v1")
                .description("""
                        Catalog phim: phim, thể loại, nghệ sĩ.

                        Endpoint GET là công khai. Mọi thao tác ghi yêu cầu ROLE_ADMIN, lấy từ
                        header X-User-Roles do API gateway inject sau khi đã xác thực JWT.
                        Gọi thẳng service mà không qua gateway sẽ bị từ chối ở môi trường production.
                        """));
    }
}

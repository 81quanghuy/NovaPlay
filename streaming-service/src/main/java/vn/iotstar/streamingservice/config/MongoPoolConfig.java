package vn.iotstar.streamingservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bound connection pool tường minh — mặc định của driver là 100 connection/pod. HPA của
 * streaming-service scale tới 10 pod (xem k8s/streaming-service/hpa.yaml), nên không bound sẽ ra
 * trần 1000 connection tới một cụm Mongo khi scale hết cỡ. Đặt riêng property này (không nhúng
 * {@code maxPoolSize} vào MONGODB_URI) để giá trị luôn có hiệu lực bất kể URI được cấu hình ra sao.
 */
@Configuration
public class MongoPoolConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolSizeCustomizer(
            @Value("${spring.data.mongodb.pool.max-size:30}") int maxPoolSize) {
        return builder -> builder.applyToConnectionPoolSettings(
                pool -> pool.maxSize(maxPoolSize));
    }
}

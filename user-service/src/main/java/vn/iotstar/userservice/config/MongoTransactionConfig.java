package vn.iotstar.userservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * MongoDB chỉ hỗ trợ transaction khi chạy replica set. Đăng ký transaction manager mà
 * cluster không phải replica set sẽ khiến mọi thao tác ghi có {@code @Transactional} thất bại,
 * nên bean này phải được bật tường minh (mặc định tắt cho dev chạy mongod standalone).
 * <p>
 * Lưu ý phần lớn nghiệp vụ ở đây ghi một document duy nhất, mà thao tác đó vốn đã atomic
 * trong MongoDB — transaction chỉ cần khi có thao tác nhiều document thực sự.
 */
@Configuration
@ConditionalOnProperty(name = "application.mongodb.transactions-enabled", havingValue = "true")
public class MongoTransactionConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}

package vn.iotstar.userservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cố tình KHÔNG dùng {@code @SpringBootTest}.
 * <p>
 * Nạp toàn bộ context đòi hỏi MongoDB, Kafka, Redis và Eureka phải chạy sẵn ở localhost, nên
 * test sẽ hỏng trên mọi máy hay pipeline CI không dựng sẵn hạ tầng — đó chính là lý do bài
 * test cũ không bao giờ xanh. Việc kiểm chứng context khởi động được thuộc về integration
 * test có hạ tầng thật, không phải unit test.
 */
class UserServiceApplicationTests {

    @Test
    @DisplayName("lớp application được cấu hình như một Spring Boot application")
    void isAnnotatedAsSpringBootApplication() {
        assertThat(UserServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}

package vn.iotstar.notificationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cố tình KHÔNG dùng {@code @SpringBootTest}.
 * <p>
 * Nạp toàn bộ context đòi hỏi MongoDB, Redis, Kafka và Eureka phải chạy sẵn ở localhost, nên test
 * sẽ hỏng trên mọi máy hay pipeline CI không dựng sẵn hạ tầng. Việc kiểm chứng context khởi động
 * được thuộc về integration test có hạ tầng thật (xem {@code NotificationPipelineIT}), không phải
 * unit test.
 */
class NotificationServiceApplicationTests {

    @Test
    @DisplayName("lớp application được cấu hình như một Spring Boot application")
    void isAnnotatedAsSpringBootApplication() {
        assertThat(NotificationServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}

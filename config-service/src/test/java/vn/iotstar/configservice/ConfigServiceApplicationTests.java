package vn.iotstar.configservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cố tình KHÔNG dùng {@code @SpringBootTest} — nạp toàn bộ context đòi hỏi MongoDB phải chạy sẵn.
 */
class ConfigServiceApplicationTests {

    @Test
    @DisplayName("lớp application được cấu hình như một Spring Boot application")
    void isAnnotatedAsSpringBootApplication() {
        assertThat(ConfigServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}

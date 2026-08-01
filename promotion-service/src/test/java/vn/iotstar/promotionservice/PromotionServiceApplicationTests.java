package vn.iotstar.promotionservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cố tình KHÔNG dùng {@code @SpringBootTest}.
 * <p>
 * Nạp toàn bộ context đòi hỏi Postgres và Kafka phải chạy sẵn ở localhost (xem
 * {@code application-dev.yml}), nên test sẽ hỏng trên mọi máy hay pipeline CI không dựng sẵn hạ
 * tầng — nhất là sau khi bỏ {@code spring.profiles.active: dev} hardcode khỏi
 * {@code application.yml} (Global Constraints), context mặc định còn không có datasource nào để
 * dùng. Việc kiểm chứng context khởi động được thuộc về integration test có hạ tầng thật (xem
 * {@code CouponRedemptionIT}), không phải unit test — cùng convention với media-service/
 * movie-service/notification-service/user-service.
 */
class PromotionServiceApplicationTests {

    @Test
    @DisplayName("lớp application được cấu hình như một Spring Boot application")
    void isAnnotatedAsSpringBootApplication() {
        assertThat(PromotionServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}

package vn.iotstar.promotionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Không còn @ComponentScan tường minh: mọi bean dùng chung đã nằm trong vn.iotstar.promotionservice,
// nên package mặc định của @SpringBootApplication là đủ. Nhờ vậy hai filter TypeExcludeFilter và
// AutoConfigurationExcludeFilter được Boot tự thêm lại — trước đây phải khai báo tay vì
// @ComponentScan tường minh ghi đè mất chúng và làm các slice test (@WebMvcTest, @DataJpaTest)
// kéo cả JpaAuditingConfig vào context khi không có repository/EntityManagerFactory.
//
// OutboxConfiguration cũng không cần @Import nữa: nó đã nằm trong vn.iotstar.promotionservice.outbox
// nên component scan tự nhặt.
@SpringBootApplication
public class PromotionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }

}

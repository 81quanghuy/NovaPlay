package vn.iotstar.promotionservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.iotstar.promotionservice.model.entity.Coupon;
import vn.iotstar.promotionservice.model.enums.CouponType;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;
import vn.iotstar.promotionservice.repository.CouponRedemptionRepository;
import vn.iotstar.promotionservice.repository.CouponRepository;
import vn.iotstar.promotionservice.service.CouponService;
import vn.iotstar.promotionservice.service.EventPublisher;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Test quan trọng nhất của Task 4: chứng minh {@link CouponRepository#tryReserveRedemption} thật
 * sự serialize hoá các request đồng thời trên Postgres THẬT — mock repository (như
 * {@code CouponServiceImplTest}) không chứng minh được điều này, vì "trả về 0 hàng update" chỉ là
 * một con số giả lập, không phải hành vi row-lock thật của {@code UPDATE ... WHERE ...}.
 * <p>
 * Kịch bản: 1 coupon với {@code totalRedemptionLimit = 1}, 10 "người dùng" khác nhau gọi
 * {@code redeem()} đồng thời (cùng bắt đầu qua {@link CountDownLatch}, mỗi người một
 * {@code idempotencyKey} riêng để loại trừ nhánh idempotent-replay khỏi phép thử này — nhánh đó đã
 * được kiểm ở {@code CouponServiceImplTest}). Kỳ vọng: đúng 1 thành công, 9 nhận
 * {@link BadRequestException}, và {@code redeemedCount} cuối cùng trong DB đúng bằng 1 (không bị
 * lệch dương do race giữa reserve và insert, không bị lệch âm do release nhầm).
 * <p>
 * <b>KHÔNG chạy được trên máy phát triển hiện tại</b>: Testcontainers 1.21.0 (do Spring Boot 3.5.0
 * BOM ghim) yêu cầu Docker API 1.32, Docker Engine cục bộ chỉ chấp nhận API ≥1.40. Test tự bỏ qua
 * qua {@code @EnabledIf("dockerAvailable")} — xem {@code MovieRepositoryIT} để biết đúng idiom
 * này. Test đã được review kỹ bằng mắt (transaction propagation, row lock, idempotency race) vì
 * không thể chạy thử trực tiếp; CI có Docker thật sẽ chạy nó thật sự.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        // ddl-auto=update cho phép Hibernate tự tạo schema (coupons/coupon_redemptions/
        // outbox_events) trên container Postgres trống — repo này không dùng Flyway/Liquibase
        // (xem auth-service, cùng convention).
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        // Pool đủ lớn để 10 thread redeem() đồng thời không bị nghẽn chờ connection — nghẽn ở đây
        // sẽ che giấu race thật bằng race giả do connection pool, không phản ánh đúng khả năng
        // serialize của UPDATE ... WHERE ... trên Postgres.
        "spring.datasource.hikari.maximum-pool-size=20"
})
@EnabledIf("dockerAvailable")
@Import(CouponRedemptionIT.NoOpEventPublisherConfig.class)
class CouponRedemptionIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = dockerAvailable()
            ? new PostgreSQLContainer<>("postgres:16-alpine")
            : null;

    /**
     * Publish sự kiện đi qua {@code EventPublisherImpl} thật sẽ cần Kafka thật — không phải thứ
     * test này muốn kiểm chứng (đó là {@code KafkaTemplate}/outbox, đã review riêng bằng mắt vì
     * pattern nguyên vẹn từ auth-service). Mock hẳn {@link EventPublisher} để cô lập test vào
     * đúng một biến số: hành vi CAS của Postgres.
     */
    @TestConfiguration
    static class NoOpEventPublisherConfig {
        @Bean
        EventPublisher eventPublisher() {
            EventPublisher mock = org.mockito.Mockito.mock(EventPublisher.class);
            lenient().doNothing().when(mock).publish(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
            return mock;
        }
    }

    @Import(NoOpEventPublisherConfig.class)
    static class Marker {}

    @Autowired private CouponService couponService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponRedemptionRepository couponRedemptionRepository;

    private Coupon coupon;

    private static final int CONCURRENT_USERS = 10;
    private static final String PLAN_ID = "plan-premium";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(200);

    @BeforeEach
    void setUp() {
        couponRedemptionRepository.deleteAll();
        couponRepository.deleteAll();

        coupon = couponRepository.saveAndFlush(Coupon.builder()
                .code("FLASH-ONE-ONLY")
                .type(CouponType.FIXED_AMOUNT)
                .value(BigDecimal.valueOf(50))
                .startAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .endAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .totalRedemptionLimit(1)
                .redeemedCount(0)
                .perUserRedemptionLimit(1)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("10 người dùng khác nhau redeem đồng thời một coupon totalRedemptionLimit=1 -> đúng 1 thành công")
    void concurrentRedemptions_onlyOneSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger badRequestCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        IntStream.range(0, CONCURRENT_USERS).forEach(i -> executor.submit(() -> {
            try {
                startLatch.await();
                couponService.redeem("FLASH-ONE-ONLY", "user" + i + "@novaplay.vn", PLAN_ID, AMOUNT,
                        "idem-key-" + i);
                successCount.incrementAndGet();
            } catch (BadRequestException e) {
                badRequestCount.incrementAndGet();
            } catch (Exception e) {
                unexpectedCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        }));

        startLatch.countDown();
        boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("tất cả thread phải hoàn tất trong 60s, không bị deadlock").isTrue();
        assertThat(unexpectedCount.get()).as("không thread nào được ném exception ngoài dự kiến").isZero();
        assertThat(successCount.get()).as("đúng một request thắng cuộc").isEqualTo(1);
        assertThat(badRequestCount.get()).as("9 request còn lại bị từ chối vì hết lượt").isEqualTo(CONCURRENT_USERS - 1);

        Coupon reloaded = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(reloaded.getRedeemedCount())
                .as("redeemedCount cuối cùng phải đúng bằng 1 — không lệch dương (race reserve/insert " +
                        "không được release đúng) cũng không lệch âm (release nhầm ở request thắng cuộc)")
                .isEqualTo(1);

        List<vn.iotstar.promotionservice.model.entity.CouponRedemption> confirmed =
                couponRedemptionRepository.findAll().stream()
                        .filter(r -> r.getStatus() == RedemptionStatus.CONFIRMED)
                        .toList();
        assertThat(confirmed).hasSize(1);
    }
}

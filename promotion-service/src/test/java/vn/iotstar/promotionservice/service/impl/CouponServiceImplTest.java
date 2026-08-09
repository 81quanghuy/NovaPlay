package vn.iotstar.promotionservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.promotionservice.model.entity.Coupon;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;
import vn.iotstar.promotionservice.model.enums.CouponType;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;
import vn.iotstar.promotionservice.repository.CouponRedemptionRepository;
import vn.iotstar.promotionservice.repository.CouponRepository;
import vn.iotstar.promotionservice.outbox.OutboxEventPublisher;
import vn.iotstar.promotionservice.exception.BadRequestException;
import vn.iotstar.promotionservice.exception.ForbiddenException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho logic CAS redemption của {@link CouponServiceImpl}, dùng Mockito để giả lập
 * repository — không cần Postgres thật. Phần "CAS có thực sự serialize dưới tải đồng thời thật
 * hay không" được kiểm chứng riêng ở {@code CouponRedemptionIT} (Testcontainers), vì đó là hành vi
 * của Postgres, không phải của code Java.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository couponRedemptionRepository;
    @Mock private OutboxEventPublisher eventPublisher;
    @Mock private CouponRedemptionRecoveryService recoveryService;

    private CouponServiceImpl couponService;

    private static final String USER_EMAIL = "user@novaplay.vn";
    private static final String PLAN_ID = "plan-basic";
    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    @BeforeEach
    void setUp() {
        couponService = new CouponServiceImpl(couponRepository, couponRedemptionRepository,
                eventPublisher, new ObjectMapper(), recoveryService);
    }

    private Coupon activeCoupon() {
        return Coupon.builder()
                .id(UUID.randomUUID())
                .code("SUMMER2026")
                .type(CouponType.PERCENTAGE)
                .value(BigDecimal.TEN)
                .startAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .totalRedemptionLimit(100)
                .redeemedCount(0)
                .perUserRedemptionLimit(1)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("tryReserveRedemption trả 0 hàng update (hết lượt/hết hạn/inactive) -> BadRequestException")
    void reserveReturnsZeroRows_throwsBadRequest() {
        Coupon coupon = activeCoupon();
        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(couponRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryReserveRedemption(eq(coupon.getId()), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> couponService.redeem("SUMMER2026", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hết lượt");

        // Reservation đã thất bại (0 hàng) nên không có gì để lưu hay publish.
        verify(couponRedemptionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
        // Và cũng không có gì để giải phóng — reserve chưa từng thành công.
        verify(couponRepository, never()).releaseRedemption(any());
    }

    @Test
    @DisplayName("idempotencyKey đã tồn tại -> trả lại bản ghi cũ, KHÔNG chạy lại logic, KHÔNG publish lại")
    void duplicateIdempotencyKey_returnsExistingWithoutRepublishing() {
        CouponRedemption existing = CouponRedemption.builder()
                .id(UUID.randomUUID())
                .couponId(UUID.randomUUID())
                .userEmail(USER_EMAIL)
                .planId(PLAN_ID)
                .discountAmount(BigDecimal.TEN)
                .status(RedemptionStatus.CONFIRMED)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .redeemedAt(Instant.now())
                .build();
        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        CouponRedemption result = couponService.redeem("SUMMER2026", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY);

        assertThat(result).isSameAs(existing);
        // Replay không được chạm tới coupon, không reserve, không publish lại.
        verify(couponRepository, never()).findByCode(anyString());
        verify(couponRepository, never()).tryReserveRedemption(any(), any());
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("restrictedToUserEmail không khớp -> ForbiddenException và giải phóng lại lượt đã reserve")
    void restrictedToOtherUser_throwsForbiddenAndReleasesReservation() {
        Coupon coupon = activeCoupon();
        coupon.setRestrictedToUserEmail("someone-else@novaplay.vn");
        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(couponRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryReserveRedemption(eq(coupon.getId()), any(Instant.class))).thenReturn(1);

        assertThatThrownBy(() -> couponService.redeem("SUMMER2026", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY))
                .isInstanceOf(ForbiddenException.class);

        verify(couponRepository, times(1)).releaseRedemption(coupon.getId());
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("đã đạt perUserRedemptionLimit -> BadRequestException và giải phóng lại lượt đã reserve")
    void perUserLimitExceeded_throwsBadRequestAndReleasesReservation() {
        Coupon coupon = activeCoupon();
        coupon.setPerUserRedemptionLimit(1);
        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(couponRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryReserveRedemption(eq(coupon.getId()), any(Instant.class))).thenReturn(1);
        when(couponRedemptionRepository.countByCouponIdAndUserEmailAndStatus(
                coupon.getId(), USER_EMAIL, RedemptionStatus.CONFIRMED)).thenReturn(1L);

        assertThatThrownBy(() -> couponService.redeem("SUMMER2026", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY))
                .isInstanceOf(BadRequestException.class);

        verify(couponRepository, times(1)).releaseRedemption(coupon.getId());
    }

    @Test
    @DisplayName("redeem thành công tính đúng discount PERCENTAGE có trần maxDiscountAmount, publish 2 sự kiện")
    void successfulRedeem_calculatesDiscountAndPublishesEvents() {
        Coupon coupon = activeCoupon();
        coupon.setType(CouponType.PERCENTAGE);
        coupon.setValue(BigDecimal.valueOf(50)); // 50%
        coupon.setMaxDiscountAmount(BigDecimal.valueOf(20));

        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(couponRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryReserveRedemption(eq(coupon.getId()), any(Instant.class))).thenReturn(1);
        when(couponRedemptionRepository.countByCouponIdAndUserEmailAndStatus(
                coupon.getId(), USER_EMAIL, RedemptionStatus.CONFIRMED)).thenReturn(0L);
        when(couponRedemptionRepository.saveAndFlush(any(CouponRedemption.class)))
                .thenAnswer(inv -> {
                    // Mô phỏng Hibernate gán id lúc persist (GenerationType.UUID) — entity chưa
                    // save() thật thì id vẫn null, khác với hành vi DB thật.
                    CouponRedemption arg = inv.getArgument(0);
                    arg.setId(UUID.randomUUID());
                    return arg;
                });

        CouponRedemption result = couponService.redeem("SUMMER2026", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY);

        // 50% của 100 = 50, nhưng trần maxDiscountAmount=20 nên chỉ được giảm 20.
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(result.getStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        verify(eventPublisher, times(2)).publish(anyString(), anyString(), any());
        verify(couponRepository, never()).releaseRedemption(any());
    }

    @Test
    @DisplayName("coupon không tồn tại -> ResourceNotFoundException")
    void couponNotFound_throwsResourceNotFound() {
        when(couponRedemptionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.redeem("NOPE", USER_EMAIL, PLAN_ID,
                BigDecimal.valueOf(100), IDEMPOTENCY_KEY))
                .isInstanceOf(vn.iotstar.promotionservice.exception.ResourceNotFoundException.class);

        verify(couponRepository, never()).tryReserveRedemption(any(), any());
    }
}

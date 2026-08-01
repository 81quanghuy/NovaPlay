package vn.iotstar.utils.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Coupon vừa được đổi thành công. Producer: promotion-service, topic {@code redeem-coupon.v1}.
 *
 * @param redemptionId  business id của {@code CouponRedemption} (không phải Kafka offset) —
 *                      report-service (Task 8) dùng đúng field này làm khoá idempotency.
 * @param couponId      id của coupon đã dùng.
 * @param couponCode    mã coupon, để consumer không phải join ngược lại promotion-service.
 * @param userEmail     người đã đổi coupon.
 * @param planId        gói/đơn hàng mà coupon được áp dụng vào.
 * @param discountAmount số tiền được giảm.
 * @param redeemedAt    thời điểm đổi coupon thành công.
 */
public record CouponRedeemedEvent(
        String redemptionId,
        String couponId,
        String couponCode,
        String userEmail,
        String planId,
        BigDecimal discountAmount,
        Instant redeemedAt
) {}

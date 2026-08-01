package vn.iotstar.utils.dto;

import java.time.Instant;

/**
 * Referral đủ điều kiện, phần thưởng đã phát hành. Producer: promotion-service, topic
 * {@code qualify-referral.v1}. Dùng ở Task 5.
 *
 * @param referralId              business id của bản ghi referral.
 * @param referrerEmail           người giới thiệu.
 * @param refereeEmail            người được giới thiệu.
 * @param referrerRewardCouponCode mã coupon thưởng phát cho người giới thiệu.
 * @param refereeRewardCouponCode  mã coupon thưởng phát cho người được giới thiệu.
 * @param qualifiedAt             thời điểm referral đủ điều kiện.
 */
public record ReferralQualifiedEvent(
        String referralId,
        String referrerEmail,
        String refereeEmail,
        String referrerRewardCouponCode,
        String refereeRewardCouponCode,
        Instant qualifiedAt
) {}

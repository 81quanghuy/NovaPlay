package vn.iotstar.utils.dto;

import java.time.Instant;

/**
 * Một lời mời giới thiệu vừa được ghi nhận (PENDING). Producer: promotion-service, topic
 * {@code create-referral.v1}.
 * <p>
 * Khai báo ở đây cùng lúc với {@link CouponRedeemedEvent} vì đang sửa cùng file
 * {@code TopicNames}, dù bản thân referral domain thuộc Task 5.
 *
 * @param referralId    business id của bản ghi referral.
 * @param referrerEmail người giới thiệu.
 * @param refereeEmail  người được giới thiệu.
 * @param createdAt     thời điểm ghi nhận.
 */
public record ReferralCreatedEvent(
        String referralId,
        String referrerEmail,
        String refereeEmail,
        Instant createdAt
) {}

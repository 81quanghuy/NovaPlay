package vn.iotstar.promotionservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;
import vn.iotstar.utils.AbstractBaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CouponRedemption extends AbstractBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RedemptionStatus status;

    /**
     * Khoá idempotency do caller sinh — trùng key nghĩa là retry của cùng một yêu cầu, không phải
     * một lần đổi coupon mới. UNIQUE để Postgres tự chặn race giữa hai request mang cùng key
     * (xem {@code CouponServiceImpl#redeem}).
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}

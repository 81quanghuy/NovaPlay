package vn.iotstar.promotionservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.iotstar.promotionservice.model.enums.CouponType;
import vn.iotstar.promotionservice.common.audit.AbstractBaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Coupon extends AbstractBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    /** Trần giảm giá khi {@code type = PERCENTAGE}; không có ý nghĩa với {@code FIXED_AMOUNT}. */
    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    /**
     * Danh sách planId được phép áp dụng, lưu dưới dạng chuỗi JSON array (ví dụ
     * {@code ["plan-1","plan-2"]}). {@code null} nghĩa là áp dụng cho mọi plan.
     * <p>
     * Cố tình khai báo kiểu {@code String} thay vì {@code List<String>}: cột là {@code jsonb} thô,
     * việc (de)serialize sang {@code List<String>} thuộc về tầng service (xem
     * {@code CouponServiceImpl}), không phải tầng entity — tránh phải kéo thêm một
     * {@code AttributeConverter}/Hypersistence chỉ để dùng cho một field.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicable_plan_ids", columnDefinition = "jsonb")
    private String applicablePlanIds;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** {@code null} nghĩa là không giới hạn tổng số lượt đổi. */
    @Column(name = "total_redemption_limit")
    private Integer totalRedemptionLimit;

    @Column(name = "redeemed_count", nullable = false)
    @Builder.Default
    private Integer redeemedCount = 0;

    @Column(name = "per_user_redemption_limit", nullable = false)
    @Builder.Default
    private Integer perUserRedemptionLimit = 1;

    /** Set cho coupon thưởng referral, chặn lộ code cho người dùng khác dùng ké. */
    @Column(name = "restricted_to_user_email")
    private String restrictedToUserEmail;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}

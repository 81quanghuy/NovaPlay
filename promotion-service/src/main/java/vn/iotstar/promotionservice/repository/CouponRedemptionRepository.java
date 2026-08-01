package vn.iotstar.promotionservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;

import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    /**
     * Kiểm tra giới hạn lượt-mỗi-người. An toàn để chạy SAU {@link CouponRepository#tryReserveRedemption}
     * trong CÙNG một transaction mà không cần lock thêm: lệnh {@code UPDATE} đó đã khoá row của
     * coupon và serialize hoá mọi request cùng đổi coupon này, nên tại thời điểm COUNT chạy,
     * không có redemption nào khác của cùng coupon đang "lơ lửng" giữa chừng.
     */
    long countByCouponIdAndUserEmailAndStatus(UUID couponId, String userEmail, RedemptionStatus status);

    Optional<CouponRedemption> findByIdempotencyKey(String idempotencyKey);

    Page<CouponRedemption> findByUserEmail(String userEmail, Pageable pageable);
}

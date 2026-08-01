package vn.iotstar.promotionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.iotstar.promotionservice.model.entity.Coupon;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    /**
     * Compare-and-swap: chỉ tăng {@code redeemedCount} nếu coupon vẫn còn hiệu lực (active, trong
     * khoảng thời gian, chưa hết lượt). Postgres giữ row lock trong lệnh {@code UPDATE} này nên
     * mọi request cùng đổi một coupon tự động serialize hoá — đây CHÍNH LÀ cơ chế concurrency-safe
     * của toàn bộ luồng redeem, không cần thêm {@code SELECT ... FOR UPDATE} hay lock phân tán.
     * <p>
     * Trả về 0 hàng bị update nghĩa là hết lượt/hết hạn/inactive; caller phải ném
     * {@code BadRequestException} khi thấy giá trị này.
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.redeemedCount = c.redeemedCount + 1, c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.id = :id AND c.active = true AND :now BETWEEN c.startAt AND c.endAt " +
            "AND (c.totalRedemptionLimit IS NULL OR c.redeemedCount < c.totalRedemptionLimit)")
    int tryReserveRedemption(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Trả lại một lượt đã reserve (rollback nghiệp vụ sau khi {@link #tryReserveRedemption} đã
     * thành công nhưng bước tiếp theo thất bại, hoặc khi admin huỷ một redemption đã CONFIRMED).
     * Guard {@code redeemedCount > 0} để không bao giờ âm.
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.redeemedCount = c.redeemedCount - 1, c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.id = :id AND c.redeemedCount > 0")
    int releaseRedemption(@Param("id") UUID id);
}

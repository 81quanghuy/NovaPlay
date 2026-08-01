package vn.iotstar.promotionservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vn.iotstar.promotionservice.model.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Body tạo/cập nhật coupon. Dùng chung cho POST và PUT. */
@Schema(title = "CouponRequest")
public record CouponRequest(

        @NotBlank
        @Schema(description = "Mã coupon, duy nhất", example = "SUMMER2026")
        String code,

        @NotNull
        CouponType type,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Schema(description = "Phần trăm (0-100) nếu type=PERCENTAGE, hoặc số tiền cố định nếu type=FIXED_AMOUNT")
        BigDecimal value,

        @Schema(description = "Đơn hàng phải đạt tối thiểu số tiền này mới áp dụng được coupon; null = không giới hạn")
        BigDecimal minOrderAmount,

        @Schema(description = "Trần giảm giá khi type=PERCENTAGE; null = không giới hạn")
        BigDecimal maxDiscountAmount,

        @Schema(description = "Danh sách planId được áp dụng; null hoặc rỗng = áp dụng mọi plan")
        List<String> applicablePlanIds,

        @NotNull
        Instant startAt,

        @NotNull
        Instant endAt,

        @Positive
        @Schema(description = "Tổng số lượt đổi tối đa; null = không giới hạn")
        Integer totalRedemptionLimit,

        @NotNull
        @Positive
        @Schema(description = "Số lượt đổi tối đa cho mỗi người dùng")
        Integer perUserRedemptionLimit,

        @Schema(description = "Chỉ cho phép đúng email này đổi coupon; null = ai cũng đổi được (nếu biết mã)")
        String restrictedToUserEmail,

        // Không @NotNull: "active" là kiểu nguyên thuỷ (boolean), không bao giờ null nên annotation
        // sẽ không có tác dụng gì — false vẫn là một giá trị hợp lệ (tạo coupon ở trạng thái tắt).
        boolean active
) {}

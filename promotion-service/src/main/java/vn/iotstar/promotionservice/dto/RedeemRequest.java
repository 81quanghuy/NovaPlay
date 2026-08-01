package vn.iotstar.promotionservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(title = "RedeemRequest")
public record RedeemRequest(

        @NotBlank
        String planId,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal amount,

        @NotBlank
        @Schema(description = "Khoá idempotency do client sinh — retry cùng key trả lại đúng kết quả cũ, không đổi coupon lần nữa")
        String idempotencyKey
) {}

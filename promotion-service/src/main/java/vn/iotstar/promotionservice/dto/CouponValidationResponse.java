package vn.iotstar.promotionservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Kết quả preview áp mã giảm giá, không có side effect (không gọi tryReserveRedemption). */
@Schema(title = "CouponValidationResponse")
public record CouponValidationResponse(
        String code,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {}

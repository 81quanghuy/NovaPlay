package vn.iotstar.promotionservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.promotionservice.dto.CouponRequest;
import vn.iotstar.promotionservice.dto.CouponValidationResponse;
import vn.iotstar.promotionservice.model.entity.Coupon;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;

import java.math.BigDecimal;
import java.util.UUID;

public interface CouponService {

    Coupon create(CouponRequest request);

    Coupon update(UUID id, CouponRequest request);

    /** Soft delete: {@code active=false}. Không hard-delete để giữ FK history cho coupon_redemptions. */
    void delete(UUID id);

    Coupon getById(UUID id);

    Page<Coupon> list(Pageable pageable);

    /** Preview discount, KHÔNG gọi {@code tryReserveRedemption} — không có side effect. */
    CouponValidationResponse validate(String code, String userEmail, String planId, BigDecimal amount);

    CouponRedemption redeem(String code, String userEmail, String planId, BigDecimal amount, String idempotencyKey);

    CouponRedemption cancelRedemption(UUID redemptionId);

    Page<CouponRedemption> myRedemptions(String userEmail, Pageable pageable);
}

package vn.iotstar.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.promotionservice.dto.CouponRequest;
import vn.iotstar.promotionservice.dto.RedeemRequest;
import vn.iotstar.promotionservice.service.CouponService;
import vn.iotstar.utils.constants.GenericResponse;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon giảm giá: quản trị cần ROLE_ADMIN, đổi/xem lịch sử cần đăng nhập.")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Tạo coupon")
    public ResponseEntity<GenericResponse> create(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.success(
                couponService.create(request), "Đã tạo coupon", HttpStatus.CREATED.value()));
    }

    @GetMapping("/coupons")
    // Lớp filter chain (SecurityConfig) chỉ áp anyRequest().authenticated() cho GET vì path này
    // trùng prefix với GET .../validate (public-authenticated); enforce ADMIN cho đúng danh sách
    // này ở tầng controller.
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Danh sách coupon, có phân trang")
    public ResponseEntity<GenericResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> result = couponService.list(PageRequest.of(page, size));
        return ResponseEntity.ok(GenericResponse.success(result));
    }

    @GetMapping("/coupons/{code}/validate")
    @Operation(summary = "Preview số tiền được giảm; không side effect, không tăng redeemedCount")
    public ResponseEntity<GenericResponse> validate(
            @PathVariable String code,
            Authentication authentication,
            @RequestParam String planId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(GenericResponse.success(
                couponService.validate(code, authentication.getName(), planId, amount)));
    }

    @PutMapping("/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Cập nhật coupon")
    public ResponseEntity<GenericResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(
                GenericResponse.success(couponService.update(id, request), "Đã cập nhật coupon"));
    }

    @DeleteMapping("/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Vô hiệu hoá coupon (soft delete, giữ lại lịch sử redemption)")
    public ResponseEntity<GenericResponse> delete(@PathVariable UUID id) {
        couponService.delete(id);
        return ResponseEntity.ok(GenericResponse.ok("Đã vô hiệu hoá coupon"));
    }

    @PostMapping("/coupons/{code}/redeem")
    @Operation(summary = "Đổi coupon; concurrency-safe qua CAS trên redeemedCount")
    public ResponseEntity<GenericResponse> redeem(
            @PathVariable String code,
            Authentication authentication,
            @Valid @RequestBody RedeemRequest request) {
        return ResponseEntity.ok(GenericResponse.success(
                couponService.redeem(code, authentication.getName(), request.planId(),
                        request.amount(), request.idempotencyKey()),
                "Đã áp dụng mã giảm giá"));
    }

    @PostMapping("/redemptions/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Huỷ một redemption; trả lại lượt cho coupon")
    public ResponseEntity<GenericResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(
                GenericResponse.success(couponService.cancelRedemption(id), "Đã huỷ redemption"));
    }

    @GetMapping("/redemptions/me")
    @Operation(summary = "Lịch sử redemption của người dùng hiện tại")
    public ResponseEntity<GenericResponse> myRedemptions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> result = couponService.myRedemptions(authentication.getName(), PageRequest.of(page, size));
        return ResponseEntity.ok(GenericResponse.success(result));
    }
}

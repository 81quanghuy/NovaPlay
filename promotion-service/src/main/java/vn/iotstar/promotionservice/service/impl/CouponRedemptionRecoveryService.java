package vn.iotstar.promotionservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;
import vn.iotstar.promotionservice.repository.CouponRedemptionRepository;

/**
 * Bean riêng biệt CHỈ để chạy truy vấn phục hồi trong một transaction MỚI (REQUIRES_NEW).
 * <p>
 * Lý do tồn tại: khi {@link CouponServiceImpl#redeem} bắt {@code DataIntegrityViolationException}
 * từ va chạm UNIQUE trên {@code idempotency_key}, Postgres đã đánh dấu TOÀN BỘ transaction hiện
 * tại là aborted — mọi câu lệnh SQL tiếp theo trên CÙNG connection/transaction đó sẽ ném lỗi
 * "current transaction is aborted, commands ignored until end of transaction block", kể cả một
 * SELECT vô hại. Bean này bị gọi từ trong catch block đó, mở một transaction hoàn toàn mới (kết
 * nối khác) để tra lại bản ghi mà request thắng cuộc đã insert, độc lập với transaction đã hỏng
 * của request thua cuộc. Không thể đạt hiệu ứng này bằng self-invocation trong cùng bean vì Spring
 * AOP proxy không chặn được lời gọi nội bộ (this.method()).
 */
@Service
@RequiredArgsConstructor
public class CouponRedemptionRecoveryService {

    private final CouponRedemptionRepository couponRedemptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CouponRedemption findExisting(String idempotencyKey) {
        return couponRedemptionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }
}

package vn.iotstar.promotionservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import vn.iotstar.promotionservice.dto.CouponRequest;
import vn.iotstar.promotionservice.dto.CouponValidationResponse;
import vn.iotstar.promotionservice.model.entity.Coupon;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;
import vn.iotstar.promotionservice.model.enums.CouponType;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;
import vn.iotstar.promotionservice.repository.CouponRedemptionRepository;
import vn.iotstar.promotionservice.repository.CouponRepository;
import vn.iotstar.promotionservice.service.CouponService;
import vn.iotstar.promotionservice.outbox.OutboxEventPublisher;
import vn.iotstar.promotionservice.util.TopicNames;
import vn.iotstar.promotionservice.common.dto.CouponRedeemedEvent;
import vn.iotstar.promotionservice.common.dto.NotificationRequested;
import vn.iotstar.promotionservice.exception.BadRequestException;
import vn.iotstar.promotionservice.exception.ForbiddenException;
import vn.iotstar.promotionservice.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final OutboxEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final CouponRedemptionRecoveryService recoveryService;

    @Override
    @Transactional
    public Coupon create(CouponRequest request) {
        Coupon coupon = Coupon.builder()
                .code(request.code())
                .type(request.type())
                .value(request.value())
                .minOrderAmount(request.minOrderAmount())
                .maxDiscountAmount(request.maxDiscountAmount())
                .applicablePlanIds(toJson(request.applicablePlanIds()))
                .startAt(request.startAt())
                .endAt(request.endAt())
                .totalRedemptionLimit(request.totalRedemptionLimit())
                .redeemedCount(0)
                .perUserRedemptionLimit(request.perUserRedemptionLimit())
                .restrictedToUserEmail(request.restrictedToUserEmail())
                .active(request.active())
                .build();
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public Coupon update(UUID id, CouponRequest request) {
        Coupon coupon = getById(id);
        coupon.setCode(request.code());
        coupon.setType(request.type());
        coupon.setValue(request.value());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setApplicablePlanIds(toJson(request.applicablePlanIds()));
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        coupon.setTotalRedemptionLimit(request.totalRedemptionLimit());
        coupon.setPerUserRedemptionLimit(request.perUserRedemptionLimit());
        coupon.setRestrictedToUserEmail(request.restrictedToUserEmail());
        coupon.setActive(request.active());
        // redeemedCount cố tình không bị ghi đè bởi update: nó là bộ đếm nghiệp vụ do
        // tryReserveRedemption/releaseRedemption quản lý, không phải một field CRUD thường.
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Coupon coupon = getById(id);
        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    @Override
    public Coupon getById(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy coupon: " + id));
    }

    @Override
    public Page<Coupon> list(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Override
    public CouponValidationResponse validate(String code, String userEmail, String planId, BigDecimal amount) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá: " + code));

        assertEligible(coupon, userEmail, planId, amount, Instant.now());

        BigDecimal discountAmount = calculateDiscount(coupon, amount);
        BigDecimal finalAmount = amount.subtract(discountAmount);
        return new CouponValidationResponse(coupon.getCode(), amount, discountAmount, finalAmount);
    }

    @Override
    @Transactional
    public CouponRedemption redeem(String code, String userEmail, String planId, BigDecimal amount,
                                    String idempotencyKey) {
        // Bước 1: idempotent replay — key đã tồn tại nghĩa là đây là retry của một request đã xử
        // lý xong, KHÔNG chạy lại logic bên dưới (không tính discount lại, không publish lại).
        Optional<CouponRedemption> existing = couponRedemptionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Idempotent replay for key={}, returning existing redemption id={}",
                    idempotencyKey, existing.get().getId());
            return existing.get();
        }

        // Bước 2
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá: " + code));

        // Bước 3: CAS. Postgres khoá row của coupon trong lệnh UPDATE này nên mọi request cùng
        // đổi coupon này tự động serialize hoá — đây là toàn bộ cơ chế concurrency-safe.
        Instant now = Instant.now();
        int reserved = couponRepository.tryReserveRedemption(coupon.getId(), now);
        if (reserved == 0) {
            throw new BadRequestException("Coupon hết lượt, hết hạn hoặc không còn hiệu lực");
        }

        try {
            // Bước 4
            if (coupon.getRestrictedToUserEmail() != null
                    && !coupon.getRestrictedToUserEmail().equalsIgnoreCase(userEmail)) {
                throw new ForbiddenException("Mã giảm giá này không áp dụng cho tài khoản của bạn");
            }

            // Bước 5: an toàn để COUNT ở đây mà không cần lock thêm — UPDATE ở bước 3 đã serialize
            // mọi request cùng coupon này.
            long userRedemptions = couponRedemptionRepository.countByCouponIdAndUserEmailAndStatus(
                    coupon.getId(), userEmail, RedemptionStatus.CONFIRMED);
            if (userRedemptions >= coupon.getPerUserRedemptionLimit()) {
                throw new BadRequestException("Bạn đã dùng hết lượt cho mã giảm giá này");
            }

            // Kiểm tra thêm minOrderAmount/applicablePlanIds: hai field này tồn tại trên entity
            // nhưng brief không liệt kê bước kiểm tra riêng — bỏ qua sẽ khiến chúng vô nghĩa, nên
            // enforce ở đây cùng nhóm điều kiện áp dụng, ngay trước khi tính discount.
            assertApplicable(coupon, planId, amount);

            // Bước 6
            BigDecimal discountAmount = calculateDiscount(coupon, amount);

            // Bước 7
            CouponRedemption redemption = CouponRedemption.builder()
                    .couponId(coupon.getId())
                    .userEmail(userEmail)
                    .planId(planId)
                    .discountAmount(discountAmount)
                    .status(RedemptionStatus.CONFIRMED)
                    .idempotencyKey(idempotencyKey)
                    .redeemedAt(now)
                    .build();
            // saveAndFlush (không phải save) là bắt buộc: GenerationType.UUID cho phép Hibernate
            // hoãn câu lệnh INSERT tới lúc flush cuối transaction, tức là SAU khi method này đã
            // return — lúc đó DataIntegrityViolationException sẽ ném ra ở tầng proxy transaction,
            // ngoài phạm vi try/catch bên dưới, và request thua cuộc sẽ nhận lỗi 500 thay vì được
            // trả lại bản ghi đã có. Flush ngay tại đây buộc va chạm UNIQUE xảy ra trong try/catch.
            CouponRedemption saved = couponRedemptionRepository.saveAndFlush(redemption);

            // Bước 8 + 9
            eventPublisher.publish(TopicNames.REDEEM_COUPON, saved.getId().toString(),
                    new CouponRedeemedEvent(saved.getId().toString(), coupon.getId().toString(), coupon.getCode(),
                            userEmail, planId, discountAmount, saved.getRedeemedAt()));

            eventPublisher.publish(TopicNames.NOTIFICATION_REQUESTED, saved.getId().toString(),
                    new NotificationRequested(saved.getId().toString(), null, userEmail, "GENERIC",
                            Set.of("IN_APP"), null,
                            Map.of("title", "Áp dụng mã giảm giá thành công",
                                    "body", "Bạn vừa dùng mã " + code + " và được giảm "
                                            + discountAmount + " cho đơn hàng.")));

            return saved;
        } catch (DataIntegrityViolationException e) {
            // Race hiếm giữa bước 1 và bước 7: hai request mang CÙNG idempotencyKey đều không
            // thấy bản ghi tồn tại ở bước 1 (chưa insert), cả hai đều reserve thành công ở bước 3,
            // rồi chỉ một request insert được nhờ UNIQUE constraint trên idempotencyKey.
            //
            // QUAN TRỌNG: statement INSERT thất bại khiến Postgres đánh dấu TOÀN BỘ transaction
            // hiện tại là aborted — mọi câu lệnh SQL tiếp theo trên CÙNG connection/transaction sẽ
            // ném "current transaction is aborted". Vì vậy ở đây KHÔNG được gọi
            // couponRepository.releaseRedemption(...) (nó sẽ tự ném lỗi, và dù có chạy được cũng
            // sẽ tự rollback theo cùng transaction). Cũng KHÔNG cần gọi release: khi transaction
            // này rollback, lần tăng redeemedCount ở bước 3 của CHÍNH request này tự động bị undo
            // cùng lúc — không có gì cần giải phóng thủ công. Bản ghi phải tra lại bằng
            // CouponRedemptionRecoveryService, chạy trên một transaction MỚI hoàn toàn.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            CouponRedemption winner = recoveryService.findExisting(idempotencyKey);
            if (winner == null) {
                // Không giải thích được — không phải do race idempotency key (nếu vậy đã tìm thấy
                // bản ghi thắng cuộc). Ném lại lỗi gốc thay vì nuốt một tình huống bất thường.
                throw e;
            }
            return winner;
        } catch (RuntimeException e) {
            // Lỗi nghiệp vụ thuần (restricted email, vượt per-user limit, không đủ điều kiện áp
            // dụng...) — KHÁC với nhánh DataIntegrityViolationException ở trên: không có statement
            // SQL nào thất bại nên transaction/connection vẫn khoẻ mạnh, gọi releaseRedemption ở
            // đây là an toàn. Bản thân việc RuntimeException này rồi được ném tiếp cũng khiến toàn
            // bộ transaction rollback (mặc định của @Transactional), nên về mặt dữ liệu cuối cùng
            // release ở đây là thừa — nhưng giữ lại tường minh để không phụ thuộc ngầm vào rollback
            // rule mặc định, và để log/ audit thấy rõ hành vi "giải phóng lượt" đúng như bước 4/5
            // yêu cầu, nếu sau này ai đó đổi sang propagation khác hoặc bắt exception ở tầng trên.
            couponRepository.releaseRedemption(coupon.getId());
            throw e;
        }
    }

    @Override
    @Transactional
    public CouponRedemption cancelRedemption(UUID redemptionId) {
        CouponRedemption redemption = couponRedemptionRepository.findById(redemptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy redemption: " + redemptionId));

        if (redemption.getStatus() == RedemptionStatus.CANCELLED) {
            // Idempotent: huỷ hai lần không được trừ redeemedCount hai lần.
            return redemption;
        }

        redemption.setStatus(RedemptionStatus.CANCELLED);
        redemption.setCancelledAt(Instant.now());
        CouponRedemption saved = couponRedemptionRepository.save(redemption);

        // CAS tương tự tryReserveRedemption, không âm nhờ guard redeemedCount > 0 trong query.
        couponRepository.releaseRedemption(redemption.getCouponId());

        return saved;
    }

    @Override
    public Page<CouponRedemption> myRedemptions(String userEmail, Pageable pageable) {
        return couponRedemptionRepository.findByUserEmail(userEmail, pageable);
    }

    // ---------- Nội bộ ----------

    private void assertEligible(Coupon coupon, String userEmail, String planId, BigDecimal amount, Instant now) {
        if (!coupon.isActive()) {
            throw new BadRequestException("Coupon không còn hiệu lực");
        }
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BadRequestException("Coupon chưa bắt đầu hoặc đã hết hạn");
        }
        if (coupon.getTotalRedemptionLimit() != null && coupon.getRedeemedCount() >= coupon.getTotalRedemptionLimit()) {
            throw new BadRequestException("Coupon đã hết lượt đổi");
        }
        if (coupon.getRestrictedToUserEmail() != null
                && !coupon.getRestrictedToUserEmail().equalsIgnoreCase(userEmail)) {
            throw new ForbiddenException("Mã giảm giá này không áp dụng cho tài khoản của bạn");
        }
        long userRedemptions = couponRedemptionRepository.countByCouponIdAndUserEmailAndStatus(
                coupon.getId(), userEmail, RedemptionStatus.CONFIRMED);
        if (userRedemptions >= coupon.getPerUserRedemptionLimit()) {
            throw new BadRequestException("Bạn đã dùng hết lượt cho mã giảm giá này");
        }
        assertApplicable(coupon, planId, amount);
    }

    private void assertApplicable(Coupon coupon, String planId, BigDecimal amount) {
        if (coupon.getMinOrderAmount() != null && amount.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new BadRequestException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng coupon");
        }
        List<String> applicablePlanIds = fromJson(coupon.getApplicablePlanIds());
        if (applicablePlanIds != null && !applicablePlanIds.isEmpty() && !applicablePlanIds.contains(planId)) {
            throw new BadRequestException("Coupon không áp dụng cho gói này");
        }
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal amount) {
        BigDecimal discount;
        if (coupon.getType() == CouponType.PERCENTAGE) {
            discount = amount.multiply(coupon.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
        } else {
            discount = coupon.getValue();
        }
        // Không giảm nhiều hơn giá trị đơn hàng.
        if (discount.compareTo(amount) > 0) {
            discount = amount;
        }
        return discount;
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new BadRequestException("applicablePlanIds không hợp lệ");
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Không đọc được applicablePlanIds đã lưu: {}", json, e);
            return null;
        }
    }
}

package vn.iotstar.promotionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.promotionservice.config.security.FilterRegistrationConfig;
import vn.iotstar.promotionservice.config.security.GatewayAuthFilter;
import vn.iotstar.promotionservice.config.security.HeaderAuthenticationFilter;
import vn.iotstar.promotionservice.config.security.RestAuthenticationHandlers;
import vn.iotstar.promotionservice.config.security.SecurityConfig;
import vn.iotstar.promotionservice.dto.CouponValidationResponse;
import vn.iotstar.promotionservice.model.entity.Coupon;
import vn.iotstar.promotionservice.model.entity.CouponRedemption;
import vn.iotstar.promotionservice.model.enums.CouponType;
import vn.iotstar.promotionservice.model.enums.RedemptionStatus;
import vn.iotstar.promotionservice.service.CouponService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận phân quyền của {@link CouponController} — mirror {@code MediaControllerSecurityTest}.
 * <p>
 * Kiểm trọng tâm: GET /coupons (danh sách) và GET /coupons/{code}/validate CHIA SẺ cùng prefix
 * path ({@code /api/v1/promotions/coupons/**}) nhưng có yêu cầu quyền khác nhau (ADMIN vs.
 * authenticated) — {@code SecurityConfig} không phân biệt được hai path này ở tầng filter nên
 * việc enforce ADMIN cho danh sách phải đến từ {@code @PreAuthorize} trên controller; test này xác
 * nhận lớp phòng thủ thứ hai đó thực sự hoạt động.
 */
@WebMvcTest(controllers = CouponController.class)
@Import({SecurityConfig.class, GatewayAuthFilter.class, HeaderAuthenticationFilter.class,
        FilterRegistrationConfig.class, RestAuthenticationHandlers.class,
        CouponControllerSecurityTest.TestBeans.class})
@TestPropertySource(properties = "application.security.gateway-secret.enabled=false")
class CouponControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CouponService couponService;

    @TestConfiguration
    static class TestBeans {
        /**
         * Đăng ký {@link JavaTimeModule}: {@code GenericResponse.timestamp} (java.time.Instant)
         * được serialize khi {@code GlobalExceptionHandler} trả lỗi — thiếu module này Jackson
         * ném {@code InvalidDefinitionException} lúc ghi response body.
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    private static final String ADMIN_EMAIL = "admin@novaplay.vn";
    private static final String USER_EMAIL = "user@novaplay.vn";

    private String couponRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new vn.iotstar.promotionservice.dto.CouponRequest(
                "TEST10", CouponType.PERCENTAGE, BigDecimal.TEN, null, null, null,
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null, 1, null, true));
    }

    private String redeemRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new vn.iotstar.promotionservice.dto.RedeemRequest(
                "plan-1", BigDecimal.valueOf(100), "idem-1"));
    }

    private Coupon coupon() {
        return Coupon.builder()
                .id(UUID.randomUUID())
                .code("TEST10")
                .type(CouponType.PERCENTAGE)
                .value(BigDecimal.TEN)
                .startAt(Instant.now())
                .endAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .redeemedCount(0)
                .perUserRedemptionLimit(1)
                .active(true)
                .build();
    }

    private CouponRedemption redemption() {
        return CouponRedemption.builder()
                .id(UUID.randomUUID())
                .couponId(UUID.randomUUID())
                .userEmail(USER_EMAIL)
                .planId("plan-1")
                .discountAmount(BigDecimal.TEN)
                .status(RedemptionStatus.CONFIRMED)
                .idempotencyKey("idem-1")
                .redeemedAt(Instant.now())
                .build();
    }

    // ---------- POST /coupons (create) ----------

    @Test
    @DisplayName("khách ẩn danh tạo coupon bị chặn 401")
    void anonymousCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/coupons")
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không tạo được coupon")
    void plainUserCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/coupons")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]")
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin tạo được coupon")
    void adminCanCreate() throws Exception {
        when(couponService.create(any())).thenReturn(coupon());

        mockMvc.perform(post("/api/v1/promotions/coupons")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]")
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isCreated());
    }

    // ---------- GET /coupons (list, ADMIN qua @PreAuthorize) ----------

    @Test
    @DisplayName("khách ẩn danh xem danh sách coupon bị chặn 401")
    void anonymousCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/coupons"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường KHÔNG xem được danh sách coupon (403) dù đã đăng nhập")
    void plainUserCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/coupons")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin xem được danh sách coupon")
    void adminCanList() throws Exception {
        when(couponService.list(any())).thenReturn(new PageImpl<>(List.of(coupon()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/promotions/coupons")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    // ---------- GET /coupons/{code}/validate (authenticated, KHÔNG cần ADMIN) ----------

    @Test
    @DisplayName("khách ẩn danh validate coupon bị chặn 401")
    void anonymousCannotValidate() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/coupons/TEST10/validate")
                        .param("planId", "plan-1")
                        .param("amount", "100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường (KHÔNG admin) validate được coupon — endpoint này chỉ cần đăng nhập")
    void plainUserCanValidate() throws Exception {
        when(couponService.validate(eq("TEST10"), eq(USER_EMAIL), eq("plan-1"), any()))
                .thenReturn(new CouponValidationResponse("TEST10", BigDecimal.valueOf(100),
                        BigDecimal.TEN, BigDecimal.valueOf(90)));

        mockMvc.perform(get("/api/v1/promotions/coupons/TEST10/validate")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]")
                        .param("planId", "plan-1")
                        .param("amount", "100"))
                .andExpect(status().isOk());
    }

    // ---------- PUT /coupons/{id} ----------

    @Test
    @DisplayName("khách ẩn danh cập nhật coupon bị chặn 401")
    void anonymousCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/promotions/coupons/" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không cập nhật được coupon")
    void plainUserCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/promotions/coupons/" + UUID.randomUUID())
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]")
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin cập nhật được coupon")
    void adminCanUpdate() throws Exception {
        when(couponService.update(any(), any())).thenReturn(coupon());

        mockMvc.perform(put("/api/v1/promotions/coupons/" + UUID.randomUUID())
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]")
                        .contentType("application/json")
                        .content(couponRequestJson()))
                .andExpect(status().isOk());
    }

    // ---------- DELETE /coupons/{id} ----------

    @Test
    @DisplayName("khách ẩn danh xoá coupon bị chặn 401")
    void anonymousCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/coupons/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không xoá được coupon")
    void plainUserCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/coupons/" + UUID.randomUUID())
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin xoá (vô hiệu hoá) được coupon")
    void adminCanDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/coupons/" + UUID.randomUUID())
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    // ---------- POST /coupons/{code}/redeem (authenticated, KHÔNG cần ADMIN) ----------

    @Test
    @DisplayName("khách ẩn danh đổi coupon bị chặn 401")
    void anonymousCannotRedeem() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/coupons/TEST10/redeem")
                        .contentType("application/json")
                        .content(redeemRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường đổi được coupon — không cần ADMIN")
    void plainUserCanRedeem() throws Exception {
        when(couponService.redeem(eq("TEST10"), eq(USER_EMAIL), eq("plan-1"), any(), eq("idem-1")))
                .thenReturn(redemption());

        mockMvc.perform(post("/api/v1/promotions/coupons/TEST10/redeem")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]")
                        .contentType("application/json")
                        .content(redeemRequestJson()))
                .andExpect(status().isOk());
    }

    // ---------- POST /redemptions/{id}/cancel ----------

    @Test
    @DisplayName("khách ẩn danh huỷ redemption bị chặn 401")
    void anonymousCannotCancel() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/redemptions/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không huỷ được redemption")
    void plainUserCannotCancel() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/redemptions/" + UUID.randomUUID() + "/cancel")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin huỷ được redemption")
    void adminCanCancel() throws Exception {
        when(couponService.cancelRedemption(any())).thenReturn(redemption());

        mockMvc.perform(post("/api/v1/promotions/redemptions/" + UUID.randomUUID() + "/cancel")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    // ---------- GET /redemptions/me ----------

    @Test
    @DisplayName("khách ẩn danh xem lịch sử redemption bị chặn 401")
    void anonymousCannotSeeMyRedemptions() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/redemptions/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng đã đăng nhập xem được lịch sử redemption của chính mình")
    void authenticatedUserCanSeeMyRedemptions() throws Exception {
        when(couponService.myRedemptions(eq(USER_EMAIL), any()))
                .thenReturn(new PageImpl<>(List.of(redemption()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/promotions/redemptions/me")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isOk());
    }
}

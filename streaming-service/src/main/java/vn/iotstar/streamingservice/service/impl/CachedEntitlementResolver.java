package vn.iotstar.streamingservice.service.impl;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.service.client.UserServiceClient;
import vn.iotstar.streamingservice.service.client.dto.UserProfileResponse;
import vn.iotstar.streamingservice.util.Plan;
import vn.iotstar.streamingservice.utils.CacheNames;

/**
 * Tách bean riêng cùng lý do với {@link CachedManifestResolver}: {@code @Cacheable} cần đi qua
 * AOP proxy, tự gọi method public trong cùng lớp sẽ bỏ qua cache hoàn toàn.
 * <p>
 * KHÔNG có fallbackMethod trên {@code @CircuitBreaker}: khi user-service lỗi/mở mạch, exception
 * văng thẳng ra ngoài — đây CHÍNH LÀ hành vi fail-closed mong muốn (từ chối phát thay vì cho qua).
 * Một fallback trả về "cho phép" sẽ là fail-OPEN, ngược hoàn toàn với brief bảo mật.
 */
@Component
@RequiredArgsConstructor
public class CachedEntitlementResolver {

    private final UserServiceClient userServiceClient;

    /**
     * {@code userEmail} chỉ là KHOÁ CACHE — lời gọi thật ({@code getMyProfile()}) luôn lấy danh
     * tính từ {@code SecurityContextHolder} hiện tại (qua {@code FeignSecurityConfig}), không
     * phải từ tham số này. Caller BẮT BUỘC truyền đúng {@code authentication.getName()} của
     * chính request đang xử lý — truyền email của người khác sẽ cache SAI gói cước dưới khoá đó.
     */
    @CircuitBreaker(name = "userService")
    @Cacheable(value = CacheNames.USER_PLAN, key = "#userEmail")
    public Plan resolvePlan(String userEmail) {
        UserProfileResponse profile = userServiceClient.getMyProfile().result();
        return Plan.fromName(profile == null ? null : profile.plan());
    }
}

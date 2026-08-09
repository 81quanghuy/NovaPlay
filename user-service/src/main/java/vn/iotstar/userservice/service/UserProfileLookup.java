package vn.iotstar.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.exception.ResourceNotFoundException;

/**
 * Tra cứu profile theo email — điểm chung của cả ba service impl.
 * <p>
 * Mỗi request favorites/watch-progress đều cần dịch email (từ header gateway) sang userId.
 * Việc này trước đây là một round-trip Mongo thừa trên mọi request nên kết quả được cache.
 */
@Component
@RequiredArgsConstructor
public class UserProfileLookup {

    public static final String USER_ID_CACHE = "userIdByEmail";

    private final IUserProfileRepository userProfileRepository;

    /**
     * Chỉ cache userId (chuỗi bất biến, gắn liền với email) chứ không cache cả profile —
     * profile thay đổi thường xuyên và sẽ khiến cache trả dữ liệu cũ.
     */
    @Cacheable(cacheNames = USER_ID_CACHE, key = "#email", unless = "#result == null")
    public String resolveUserId(String email) {
        return findByEmailOrThrow(email).getId();
    }

    public UserProfile findByEmailOrThrow(String email) {
        return userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /** Gọi khi profile bị xoá hoặc email đổi; cập nhật field thường thì không cần. */
    @CacheEvict(cacheNames = USER_ID_CACHE, key = "#email")
    public void evict(String email) {
        // Chỉ để kích hoạt @CacheEvict.
    }
}

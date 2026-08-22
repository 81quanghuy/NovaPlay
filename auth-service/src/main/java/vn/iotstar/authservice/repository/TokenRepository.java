package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Token;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {

    /**
     * Finds a token by its value.
     * @param tokenValue The token value to search for.
     * @return an Optional containing the found Token.
     */
    Optional<Token> findByTokenValue(String tokenValue);

    /**
     * Finds all valid (not revoked) tokens for a specific user.
     * @param userId The ID of the user.
     * @return A list of valid tokens.
     */
    List<Token> findAllByUser_IdAndIsRevokedIsFalse(UUID userId);

    /**
     * Xoá thẳng mọi token đã hết hạn bằng MỘT câu lệnh, lọc ở tầng database.
     * <p>
     * Thay cho cách cũ {@code findAll()} rồi lọc bằng Java: cách đó kéo toàn bộ bảng token vào
     * heap mỗi giờ, trên MỌI pod, rồi sinh N câu DELETE riêng lẻ — nguồn OOM chờ sẵn khi bảng lớn
     * và JVM bị giới hạn 1Gi.
     * <p>
     * Dựa vào index {@code idx_token_expired_at} (changeset 004). Không cần distributed lock:
     * câu lệnh idempotent, hai pod chạy đồng thời thì pod thứ hai đơn giản không thấy row nào.
     *
     * @return số row đã xoá
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Token t where t.expiredAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
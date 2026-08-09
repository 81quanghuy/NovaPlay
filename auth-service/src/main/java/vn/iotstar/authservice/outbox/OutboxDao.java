package vn.iotstar.authservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Toàn bộ truy cập tới bảng {@code outbox_events}.
 * <p>
 * Dùng {@link JdbcTemplate} chứ không phải JPA vì câu lệnh trung tâm của thiết kế là
 * {@code UPDATE ... RETURNING}: Hibernate 6 từ chối thực thi DML qua {@code getResultList()},
 * còn {@code JdbcTemplate.query} gọi {@code executeQuery()} mà Postgres trả ResultSet cho
 * {@code RETURNING} hoàn toàn bình thường.
 * <p>
 * {@link #insert} vẫn tham gia đúng transaction nghiệp vụ của caller: JdbcTemplate lấy connection
 * qua {@code DataSourceUtils}, mà {@code JpaTransactionManager} có bind connection JDBC vào thread
 * khi mở transaction.
 */
@RequiredArgsConstructor
@Slf4j
public class OutboxDao {

    /**
     * Backoff tính ngay trong SQL để chỉ có một nguồn sự thật. Trong vế SET, {@code attempts} là
     * giá trị CŨ, nên {@code POWER(2, attempts) * 30} cho ra đúng 2^(n-1) × 30 giây của lượt thử
     * thứ n: 30s, 60s, 120s... chạm trần 900s (15 phút).
     */
    private static final String BACKOFF_SECONDS = "LEAST(POWER(2, attempts) * 30, 900)";

    private static final String COLUMNS =
            "id, topic, event_key, payload, attempts, next_attempt_at";

    private static final RowMapper<OutboxRecord> ROW_MAPPER = (ResultSet rs, int rowNum) ->
            new OutboxRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("topic"),
                    rs.getString("event_key"),
                    rs.getString("payload"),
                    rs.getInt("attempts"),
                    toInstant(rs.getTimestamp("next_attempt_at")));

    private final JdbcTemplate jdbcTemplate;

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * Ghi ý định gửi vào bảng, trong đúng transaction của caller. Trigger {@code outbox_notify_trg}
     * sẽ phát notification khi transaction đó commit.
     */
    public UUID insert(String topic, String key, String payloadJson) {
        UUID id = UUID.randomUUID();
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, topic, event_key, payload, status, attempts, created_at, next_attempt_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, now(), now())
                """, id, topic, key, payloadJson);
            log.info("Outbox insert rows={} id={}", rows, id);
        } catch (Exception e) {
            log.error("Outbox insert FAILED", e);
            throw e;
        }
        return id;
    }

    /**
     * Tranh quyền xử lý một row cụ thể. Trả về rỗng nghĩa là pod khác đã giành được, hoặc row đã
     * bị xoá/chuyển FAILED — không phải lỗi.
     */
    public Optional<OutboxRecord> claimById(UUID id) {
        List<OutboxRecord> claimed = jdbcTemplate.query("""
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    next_attempt_at = now() + make_interval(secs => %s)
                WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= now()
                RETURNING %s
                """.formatted(BACKOFF_SECONDS, COLUMNS), ROW_MAPPER, id);
        return claimed.stream().findFirst();
    }

    /**
     * Nhận một lô row quá hạn. {@code FOR UPDATE SKIP LOCKED} khiến hai pod chạy đồng thời chia
     * nhau tập row thay vì cùng giành một tập. Câu lệnh vừa nhận việc vừa hoãn trong một lần đi
     * DB, nên transaction chỉ dài đúng một statement — không có lệnh mạng nào bên trong.
     */
    public List<OutboxRecord> claimBatch(int limit) {
        return jdbcTemplate.query("""
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    next_attempt_at = now() + make_interval(secs => %s)
                WHERE id IN (
                    SELECT id FROM outbox_events
                    WHERE status = 'PENDING' AND next_attempt_at <= now()
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING %s
                """.formatted(BACKOFF_SECONDS, COLUMNS), ROW_MAPPER, limit);
    }

    /** Gửi thành công thì xoá hẳn row — Kafka và tracing đã là bản ghi của việc đã gửi. */
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM outbox_events WHERE id = ?", id);
    }

    public void markFailed(UUID id, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE outbox_events SET status = 'FAILED', error_message = ? WHERE id = ?",
                errorMessage, id);
    }

    /** Ghi lại lỗi nhưng giữ PENDING để còn được thử lại. */
    public void recordError(UUID id, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE outbox_events SET error_message = ? WHERE id = ?", errorMessage, id);
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status = 'PENDING'", Long.class);
        return count == null ? 0L : count;
    }
}

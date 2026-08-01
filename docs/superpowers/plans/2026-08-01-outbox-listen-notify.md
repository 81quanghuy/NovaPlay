# Outbox qua Postgres LISTEN/NOTIFY — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay `OutboxRelayJob` (quét bảng mỗi 5 giây) bằng cơ chế đẩy của Postgres, và gom code outbox đang bị copy ở hai service về một chỗ dùng chung.

**Architecture:** Một trigger `AFTER INSERT` trên `outbox_events` gọi `pg_notify`. Vì `pg_notify` là transactional, notification chỉ phát khi transaction COMMIT — Postgres tự cung cấp ngữ nghĩa "sau commit" cho mọi pod đang `LISTEN`. Mỗi pod giữ một connection JDBC riêng, chặn ở tầng socket chờ notification, nhận được thì tranh quyền xử lý bằng một câu `UPDATE ... RETURNING` nguyên tử rồi gửi Kafka bất đồng bộ. Gửi xong thì xoá row. Không có `@Scheduled` nào.

**Tech Stack:** Java 21, Spring Boot 3.5.0, Spring JDBC (`JdbcTemplate`), Spring Kafka, PostgreSQL 16 (`LISTEN`/`NOTIFY`, `FOR UPDATE SKIP LOCKED`), Micrometer, JUnit 5 + Mockito + AssertJ, Testcontainers.

## Global Constraints

- Java 21, Spring Boot 3.5.0, Spring Cloud 2025.0.0 — không đổi version nào.
- Code mới đặt ở package `vn.iotstar.outbox` trong module `utils`. **Không** đặt dưới `vn.iotstar.utils`: mọi service trong repo đều `@ComponentScan(basePackages = {..., "vn.iotstar.utils"})`, kể cả bốn service dùng MongoDB (`movie-service`, `media-service`, `user-service`, `notification-service`); đặt trong vùng scan đó sẽ khiến chúng nạp phải bean outbox và chết context vì thiếu `DataSource` JPA lẫn `KafkaTemplate`.
- Comment trong code viết bằng tiếng Việt, theo đúng quy ước đang có của repo.
- Mọi dependency **compile** thêm vào `utils/pom.xml` dùng scope `provided` để không transitive sang service không dùng. Dependency chỉ phục vụ test thì dùng scope `test` như bình thường.
- Không đụng `payment-service`, `report-service`, `streaming-service`, và không chuyển service MongoDB nào sang outbox.
- Commit message kết thúc bằng dòng `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Chạy build một module: `./mvnw test -pl <module>`. Sau khi sửa `utils` phải `./mvnw install -pl utils -DskipTests` trước khi build service phụ thuộc.

## Sai khác so với spec (đã cân nhắc, cố ý)

Spec mục 3 và 5 mô tả outbox là JPA entity + `JpaRepository`. Plan này dùng **`JdbcTemplate`** thay vì JPA, vì ba lý do cụ thể:

1. Câu lệnh `UPDATE ... RETURNING *` là trung tâm của thiết kế. Hibernate 6 từ chối thực thi DML qua `getResultList()`, và Spring Data `@Modifying` không trả về entity. `JdbcTemplate.query()` gọi `PreparedStatement.executeQuery()`, thứ mà Postgres trả ResultSet cho `RETURNING` một cách hoàn toàn bình thường.
2. Bỏ được yêu cầu sửa `@EntityScan` và `@EnableJpaRepositories` ở từng service — ít chỗ hỏng hơn.
3. Tránh hẳn chuyện ánh xạ `jsonb` ↔ `String` của Hibernate; với JDBC chỉ cần `CAST(? AS jsonb)`.

Việc INSERT vẫn tham gia đúng transaction nghiệp vụ của caller: `JdbcTemplate` lấy connection qua `DataSourceUtils`, mà `JpaTransactionManager` của Spring Boot có bind connection JDBC vào thread khi mở transaction. Đây là hành vi chuẩn của Spring, không phải mẹo.

Sai khác thứ hai: backoff được tính **trong SQL** (`LEAST(POWER(2, attempts) * 30, 900)` giây) thay vì ở tầng Java. Trong câu `UPDATE ... SET`, `attempts` ở vế phải là giá trị **cũ**, nên `POWER(2, attempts) * 30` cho ra đúng `2^(n-1) × 30` giây của lượt thử thứ n. Nhờ vậy chỉ có một nguồn sự thật cho backoff, và lượt thử lại trong bộ nhớ chỉ việc đọc `next_attempt_at` trả về.

---

## File Structure

**Tạo mới trong `utils/src/main/java/vn/iotstar/outbox/`:**

| File | Trách nhiệm |
|------|-------------|
| `OutboxRecord.java` | Record bất biến, một dòng outbox đã được nhận việc |
| `OutboxProperties.java` | Cấu hình `novaplay.outbox.*` |
| `OutboxDao.java` | Toàn bộ SQL: insert, claim theo id, claim theo lô, delete, mark failed |
| `OutboxEventPublisher.java` | Interface service nghiệp vụ gọi |
| `OutboxEventPublisherImpl.java` | Serialize payload + INSERT, không chạm Kafka |
| `OutboxMetrics.java` | Counter/gauge Micrometer |
| `OutboxRelayService.java` | Nhận việc → gửi Kafka → xoá/đánh dấu hỏng/hẹn thử lại |
| `OutboxCatchUp.java` | Quét lô row mồ côi, chỉ chạy lúc startup và sau reconnect |
| `OutboxNotificationListener.java` | Thread giữ connection riêng, `LISTEN`, reconnect |
| `OutboxCatchUpController.java` | `POST /internal/outbox/catch-up` chạy tay |
| `OutboxConfiguration.java` | Khai báo toàn bộ bean, service `@Import` vào |

**Xoá:** `{auth-service,promotion-service}/.../service/impl/OutboxRelayJob.java`, `.../service/impl/EventPublisherImpl.java`, `.../service/EventPublisher.java`, `.../model/entity/OutboxEvent.java`, `.../repository/OutboxEventRepository.java`.

**Sửa:** `utils/pom.xml`, `auth-service/pom.xml`, hai lớp `*Application.java`, `OtpServiceImpl`, `AuthServiceImpl`, `CouponServiceImpl`, và file SQL migration.

---

## Task 1: Dependencies và cấu hình nền cho outbox

**Files:**
- Modify: `utils/pom.xml`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxProperties.java`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxRecord.java`
- Test: `utils/src/test/java/vn/iotstar/outbox/OutboxPropertiesTest.java`

**Interfaces:**
- Consumes: không có (task đầu tiên)
- Produces: `OutboxProperties` với getter `getChannel()`, `getMaxAttempts()`, `getBatchSize()`, `getListenTimeout()`, `getReconnectInitialDelay()`, `getReconnectMaxDelay()`, `isPendingGaugeEnabled()`. `OutboxRecord(UUID id, String topic, String key, String payload, int attempts, Instant nextAttemptAt)`.

- [ ] **Step 1: Thêm dependency vào `utils/pom.xml`**

Chèn vào trong khối `<dependencies>` đang có, ngay trước thẻ đóng `</dependencies>`:

```xml
		<!-- Ba dependency dưới đây phục vụ package vn.iotstar.outbox. Scope provided vì
		     chúng không transitive: service nào dùng outbox thì tự khai báo bản của mình
		     (auth-service và promotion-service đều đã có sẵn cả ba), còn các service Mongo
		     không bị kéo Kafka và driver Postgres vào classpath một cách vô cớ. -->
		<dependency>
			<groupId>org.springframework.kafka</groupId>
			<artifactId>spring-kafka</artifactId>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>io.micrometer</groupId>
			<artifactId>micrometer-core</artifactId>
			<scope>provided</scope>
		</dependency>
```

- [ ] **Step 2: Kiểm tra `utils` vẫn build**

Run: `./mvnw -q clean install -pl utils -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Viết test thất bại cho `OutboxProperties`**

Tạo `utils/src/test/java/vn/iotstar/outbox/OutboxPropertiesTest.java`:

```java
package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPropertiesTest {

    @Test
    void giaTriMacDinhPhaiAnToanKhiKhongCauHinhGi() {
        OutboxProperties props = new OutboxProperties();

        assertThat(props.getChannel()).isEqualTo("outbox_new");
        assertThat(props.getMaxAttempts()).isEqualTo(10);
        assertThat(props.getBatchSize()).isEqualTo(100);
        assertThat(props.getListenTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getReconnectInitialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.getReconnectMaxDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.isPendingGaugeEnabled()).isTrue();
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận nó fail**

Run: `./mvnw test -pl utils -Dtest=OutboxPropertiesTest`
Expected: FAIL — lỗi biên dịch, không tìm thấy lớp `OutboxProperties`

- [ ] **Step 5: Viết `OutboxProperties`**

```java
package vn.iotstar.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình cho cơ chế outbox. Mọi giá trị đều có mặc định an toàn nên service không bắt buộc
 * khai báo gì trong {@code application-*.yml}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novaplay.outbox")
public class OutboxProperties {

    /** Tên kênh Postgres NOTIFY. Phải khớp với tên trong trigger {@code outbox_notify_trg}. */
    private String channel = "outbox_new";

    /** Số lượt gửi tối đa trước khi chuyển row sang FAILED. */
    private int maxAttempts = 10;

    /** Số row tối đa mỗi lô của một vòng catch-up. */
    private int batchSize = 100;

    /**
     * Thời gian chặn tối đa mỗi lần chờ notification. Hết thời gian này mà không có gì thì vòng
     * lặp thức dậy để kiểm tra cờ shutdown — không phát câu SQL nào.
     */
    private Duration listenTimeout = Duration.ofSeconds(30);

    /** Khoảng chờ trước lần kết nối lại đầu tiên khi mất connection LISTEN. */
    private Duration reconnectInitialDelay = Duration.ofSeconds(1);

    /** Trần của khoảng chờ kết nối lại. */
    private Duration reconnectMaxDelay = Duration.ofSeconds(30);

    /**
     * Bật gauge {@code outbox.pending}. Gauge này chạy một câu {@code count(*)} mỗi lần
     * Prometheus scrape (mặc định 15 giây), quét trên partial index nên rất rẻ. Đây là thứ duy
     * nhất phát hiện được row kẹt sau khi pod chết giữa lúc chờ thử lại — tắt đi thì mất luôn
     * khả năng cảnh báo đó.
     */
    private boolean pendingGaugeEnabled = true;
}
```

- [ ] **Step 6: Viết `OutboxRecord`**

```java
package vn.iotstar.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng outbox đã được nhận việc và đang chờ gửi sang Kafka.
 * <p>
 * Cố ý là record chứ không phải JPA entity: mọi truy cập tới bảng đều đi qua {@link OutboxDao}
 * bằng SQL thuần, vì câu lệnh trung tâm của thiết kế là {@code UPDATE ... RETURNING} mà Hibernate
 * không thực thi được qua {@code getResultList()}.
 *
 * @param nextAttemptAt mốc được phép thử lại, đã do câu lệnh nhận việc đẩy về tương lai
 */
public record OutboxRecord(
        UUID id,
        String topic,
        String key,
        String payload,
        int attempts,
        Instant nextAttemptAt) {
}
```

- [ ] **Step 7: Chạy test để xác nhận pass**

Run: `./mvnw test -pl utils -Dtest=OutboxPropertiesTest`
Expected: PASS, 1 test

- [ ] **Step 8: Commit**

```bash
git add utils/pom.xml utils/src/main/java/vn/iotstar/outbox/ utils/src/test/java/vn/iotstar/outbox/
git commit -m "$(cat <<'EOF'
feat(outbox): dựng nền package vn.iotstar.outbox trong utils

Thêm spring-kafka, driver Postgres và micrometer-core scope provided; khai
báo OutboxProperties và OutboxRecord. Đặt ngoài vn.iotstar.utils để các
service Mongo đang @ComponentScan package đó không nạp phải bean outbox.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Migration SQL — cột, index, trigger

**Files:**
- Create: `auth-service/src/main/resources/sql/002_outbox_listen_notify.sql`
- Create: `promotion-service/src/main/resources/sql/002_outbox_listen_notify.sql` (nội dung y hệt)
- Delete: `auth-service/src/main/resources/sql/create_outbox_events.sql`

**Interfaces:**
- Consumes: bảng `outbox_events` đang có
- Produces: cột `next_attempt_at`, partial index `idx_outbox_due`, trigger `outbox_notify_trg` phát trên kênh `outbox_new`

- [ ] **Step 1: Viết file migration**

Nội dung `auth-service/src/main/resources/sql/002_outbox_listen_notify.sql`:

```sql
-- Chuyển outbox từ mô hình quét định kỳ sang mô hình Postgres đẩy qua LISTEN/NOTIFY.
--
-- Cả hai service dùng ddl-auto: update, vốn không tạo được partial index lẫn trigger. File này
-- là nguồn sự thật cho schema outbox và PHẢI chạy tay trước khi triển khai bản mới.

-- 1. Mốc backoff, đồng thời là điều kiện tranh quyền xử lý giữa các pod.
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
UPDATE outbox_events SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET DEFAULT now();

-- 2. Gửi thành công thì xoá row, nên không còn trạng thái SENT lẫn cột sent_at. Nhờ vậy bảng
--    luôn gần rỗng và không cần job dọn dẹp nào.
DELETE FROM outbox_events WHERE status = 'SENT';
ALTER TABLE outbox_events DROP COLUMN IF EXISTS sent_at;

-- 3. idx_outbox_status là index trên cột cardinality thấp, càng nhiều row càng vô dụng.
--    Partial index dưới đây chỉ chứa row PENDING — tức gần như luôn rỗng.
DROP INDEX IF EXISTS idx_outbox_status;
DROP INDEX IF EXISTS idx_outbox_created_at;
CREATE INDEX IF NOT EXISTS idx_outbox_due
    ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

-- 4. pg_notify trong trigger là transactional: notification chỉ phát khi COMMIT. Đây chính là
--    thứ thay thế cho @TransactionalEventListener(AFTER_COMMIT) ở tầng ứng dụng, và nó phát cho
--    MỌI pod đang LISTEN chứ không riêng pod đã ghi row.
CREATE OR REPLACE FUNCTION outbox_notify() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('outbox_new', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events;
CREATE TRIGGER outbox_notify_trg
    AFTER INSERT ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION outbox_notify();
```

- [ ] **Step 2: Sao chép sang promotion-service và xoá file cũ**

```bash
mkdir -p promotion-service/src/main/resources/sql
cp auth-service/src/main/resources/sql/002_outbox_listen_notify.sql \
   promotion-service/src/main/resources/sql/002_outbox_listen_notify.sql
git rm auth-service/src/main/resources/sql/create_outbox_events.sql
```

- [ ] **Step 3: Kiểm tra migration chạy được trên Postgres thật**

```bash
docker compose -f docker-compose/qa/docker-compose.yml up -d postgres
sleep 5
docker compose -f docker-compose/qa/docker-compose.yml exec -T postgres \
  psql -U postgres -d authdb -c "
    CREATE TABLE IF NOT EXISTS outbox_events (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      topic VARCHAR(255) NOT NULL, event_key VARCHAR(255),
      payload JSONB NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      attempts INT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      sent_at TIMESTAMPTZ, error_message VARCHAR(2000));"
docker compose -f docker-compose/qa/docker-compose.yml exec -T postgres \
  psql -U postgres -d authdb -v ON_ERROR_STOP=1 \
  < auth-service/src/main/resources/sql/002_outbox_listen_notify.sql
```

Expected: không có lỗi, dòng cuối in `CREATE TRIGGER`.

Nếu tên database hoặc user khác, đọc `docker-compose/qa/docker-compose.yml` để lấy giá trị đúng.

- [ ] **Step 4: Kiểm tra trigger thực sự phát notification**

```bash
docker compose -f docker-compose/qa/docker-compose.yml exec -T postgres \
  psql -U postgres -d authdb -c "
    LISTEN outbox_new;
    INSERT INTO outbox_events (topic, event_key, payload)
    VALUES ('test.v1', 'k1', '{\"a\":1}'::jsonb);
    SELECT pg_sleep(0.2);"
```

Expected: psql in ra dòng `Asynchronous notification "outbox_new" with payload "<uuid>" received`.

- [ ] **Step 5: Dọn row test**

```bash
docker compose -f docker-compose/qa/docker-compose.yml exec -T postgres \
  psql -U postgres -d authdb -c "DELETE FROM outbox_events WHERE topic = 'test.v1';"
```

- [ ] **Step 6: Commit**

```bash
git add auth-service/src/main/resources/sql promotion-service/src/main/resources/sql
git commit -m "$(cat <<'EOF'
feat(outbox): migration thêm next_attempt_at, partial index và trigger pg_notify

pg_notify trong trigger là transactional nên chỉ phát khi COMMIT — đây là thứ
thay cho listener AFTER_COMMIT ở tầng ứng dụng, và phát cho mọi pod đang
LISTEN. Bỏ trạng thái SENT vì gửi xong là xoá row.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `OutboxDao` — toàn bộ SQL

**Files:**
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxDao.java`
- Test: hoãn sang Task 10 (Testcontainers) — SQL `RETURNING` và `SKIP LOCKED` không mock được có ý nghĩa

**Interfaces:**
- Consumes: `OutboxRecord`, `OutboxProperties` từ Task 1
- Produces:
  - `UUID insert(String topic, String key, String payloadJson)`
  - `Optional<OutboxRecord> claimById(UUID id)`
  - `List<OutboxRecord> claimBatch(int limit)`
  - `void delete(UUID id)`
  - `void markFailed(UUID id, String errorMessage)`
  - `void recordError(UUID id, String errorMessage)`
  - `long countPending()`

- [ ] **Step 1: Viết `OutboxDao`**

```java
package vn.iotstar.outbox;

import lombok.RequiredArgsConstructor;
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

    private static java.time.Instant toInstant(Timestamp ts) throws SQLException {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * Ghi ý định gửi vào bảng, trong đúng transaction của caller. Trigger {@code outbox_notify_trg}
     * sẽ phát notification khi transaction đó commit.
     */
    public UUID insert(String topic, String key, String payloadJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, topic, event_key, payload, status, attempts, created_at, next_attempt_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, now(), now())
                """, id, topic, key, payloadJson);
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
```

- [ ] **Step 2: Kiểm tra biên dịch**

Run: `./mvnw -q clean install -pl utils -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add utils/src/main/java/vn/iotstar/outbox/OutboxDao.java
git commit -m "$(cat <<'EOF'
feat(outbox): OutboxDao với claim nguyên tử bằng UPDATE ... RETURNING

Backoff tính trong SQL nên chỉ có một nguồn sự thật; trong vế SET thì attempts
là giá trị cũ, cho ra đúng 2^(n-1) × 30 giây của lượt thứ n. claimBatch dùng
FOR UPDATE SKIP LOCKED để hai pod chia nhau tập row.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `OutboxEventPublisher` — ghi outbox, không chạm Kafka

**Files:**
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxEventPublisher.java`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxEventPublisherImpl.java`
- Test: `utils/src/test/java/vn/iotstar/outbox/OutboxEventPublisherImplTest.java`

**Interfaces:**
- Consumes: `OutboxDao.insert(String, String, String)` từ Task 3
- Produces: `OutboxEventPublisher.publish(String topic, String key, Object payload)` — đây là API mà `OtpServiceImpl`, `AuthServiceImpl`, `CouponServiceImpl` sẽ gọi ở Task 8 và 9

- [ ] **Step 1: Viết test thất bại**

Tạo `utils/src/test/java/vn/iotstar/outbox/OutboxEventPublisherImplTest.java`:

```java
package vn.iotstar.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherImplTest {

    @Mock private OutboxDao dao;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private OutboxEventPublisherImpl publisher;

    @Test
    void publishChiGhiOutboxVaSerializePayloadThanhJson() {
        UUID generated = UUID.randomUUID();
        when(dao.insert(eq("send-email.v1"), eq("user-1"), any())).thenReturn(generated);

        publisher.publish("send-email.v1", "user-1", Map.of("otp", "123456"));

        verify(dao).insert("send-email.v1", "user-1", "{\"otp\":\"123456\"}");
    }

    @Test
    void publishKhongDuocGuiKafka() {
        // Chốt chặn cho khiếm khuyết nghiêm trọng nhất của bản cũ: EventPublisherImpl gọi
        // kafkaTemplate.send(...).get(30s) NGAY TRONG transaction nghiệp vụ, nên một transaction
        // rollback vẫn để lọt event ra ngoài. Publisher mới không được biết Kafka là gì —
        // thể hiện bằng việc lớp này không có collaborator nào ngoài dao và objectMapper.
        //
        // Lọc field static vì getDeclaredFields() trả về CẢ chúng, mà @Slf4j sinh ra một field
        // `log` static. Và cố ý không dùng .extracting(): nó sinh kiểu capture khiến
        // containsExactlyInAnyOrder(Class<OutboxDao>, ...) không biên dịch được.
        List<Class<?>> kieuCuaFieldInstance = Arrays.stream(
                        OutboxEventPublisherImpl.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();

        assertThat(kieuCuaFieldInstance)
                .containsExactlyInAnyOrder(OutboxDao.class, ObjectMapper.class);
    }

    @Test
    void payloadKhongSerializeDuocThiNemLoiDeTransactionRollback() {
        Object khongSerializeDuoc = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                throw new IllegalStateException("bom");
            }
        };

        assertThatThrownBy(() -> publisher.publish("t.v1", "k", khongSerializeDuoc))
                .isInstanceOf(OutboxSerializationException.class);

        verifyNoInteractions(dao);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `./mvnw test -pl utils -Dtest=OutboxEventPublisherImplTest`
Expected: FAIL — lỗi biên dịch, không tìm thấy `OutboxEventPublisherImpl` và `OutboxSerializationException`

- [ ] **Step 3: Viết interface, exception và implementation**

`utils/src/main/java/vn/iotstar/outbox/OutboxEventPublisher.java`:

```java
package vn.iotstar.outbox;

/**
 * Điểm vào duy nhất để service nghiệp vụ phát sự kiện ra ngoài.
 * <p>
 * Gọi trong một method đã có {@code @Transactional}: bản ghi outbox đi theo đúng số phận của
 * transaction đó. Commit thì sự kiện chắc chắn được gửi; rollback thì không có gì lọt ra ngoài.
 */
public interface OutboxEventPublisher {

    /**
     * @param topic tên topic Kafka, lấy từ {@code vn.iotstar.utils.constants.TopicNames}
     * @param key   khoá phân vùng, quyết định thứ tự giữa các message cùng khoá
     */
    void publish(String topic, String key, Object payload);
}
```

`utils/src/main/java/vn/iotstar/outbox/OutboxSerializationException.java`:

```java
package vn.iotstar.outbox;

/**
 * Payload không serialize được thành JSON. Cố ý là unchecked và cố ý ném ra ngoài thay vì nuốt:
 * transaction nghiệp vụ phải rollback, vì một sự kiện không ghi được thì hành động sinh ra nó
 * cũng không được phép coi là đã xong.
 */
public class OutboxSerializationException extends RuntimeException {

    public OutboxSerializationException(String topic, Throwable cause) {
        super("Không serialize được payload cho topic " + topic, cause);
    }
}
```

`utils/src/main/java/vn/iotstar/outbox/OutboxEventPublisherImpl.java`:

```java
package vn.iotstar.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Ghi ý định gửi vào bảng outbox và dừng ở đó.
 * <p>
 * Lớp này không có collaborator nào liên quan tới Kafka, và đó là điểm mấu chốt: bản cũ
 * ({@code EventPublisherImpl}) gọi {@code kafkaTemplate.send(...).get(30s)} ngay trong transaction
 * nghiệp vụ, nên nếu transaction rollback sau đó thì message đã bay sang Kafka rồi. Việc gửi thật
 * do {@link OutboxRelayService} đảm nhiệm, và chỉ khởi động sau khi Postgres xác nhận đã COMMIT.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final OutboxDao dao;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, String key, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OutboxSerializationException(topic, e);
        }

        UUID id = dao.insert(topic, key, payloadJson);
        log.debug("Đã ghi outbox: id={}, topic={}, key={}", id, topic, key);
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `./mvnw test -pl utils -Dtest=OutboxEventPublisherImplTest`
Expected: PASS, 3 tests

- [ ] **Step 5: Commit**

```bash
git add utils/src/main/java/vn/iotstar/outbox/ utils/src/test/java/vn/iotstar/outbox/
git commit -m "$(cat <<'EOF'
feat(outbox): OutboxEventPublisher chỉ ghi bảng, không chạm Kafka

Sửa khiếm khuyết nghiêm trọng nhất của bản cũ: EventPublisherImpl gửi Kafka
ngay trong transaction nghiệp vụ nên transaction rollback vẫn để lọt event.
Có test chốt rằng lớp publisher không giữ collaborator Kafka nào.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `OutboxMetrics` và `OutboxRelayService`

**Files:**
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxMetrics.java`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxRelayService.java`
- Test: `utils/src/test/java/vn/iotstar/outbox/OutboxRelayServiceTest.java`

**Interfaces:**
- Consumes: `OutboxDao` (Task 3), `OutboxRecord` và `OutboxProperties` (Task 1)
- Produces:
  - `OutboxRelayService.relay(UUID id)` — dùng cho đường notification
  - `OutboxRelayService.send(OutboxRecord record)` — dùng cho đường catch-up, giả định row đã được nhận việc
  - `OutboxMetrics.published()`, `OutboxMetrics.relayFailed()`, `OutboxMetrics.terminallyFailed()`

- [ ] **Step 1: Viết `OutboxMetrics`**

```java
package vn.iotstar.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Ba counter và một gauge, đủ để dựng cảnh báo Grafana cho outbox.
 * <p>
 * Gauge {@code outbox.pending} là thứ duy nhất phát hiện được row kẹt trong tình huống pod chết
 * giữa lúc chờ thử lại (xem mục 6 của spec). Nó chạy một {@code count(*)} mỗi lần Prometheus
 * scrape; đây là chi phí có chủ ý, tắt được qua {@code novaplay.outbox.pending-gauge-enabled}.
 */
public class OutboxMetrics {

    private final Counter published;
    private final Counter relayFailed;
    private final Counter terminallyFailed;

    public OutboxMetrics(MeterRegistry registry, OutboxDao dao, OutboxProperties properties) {
        this.published = Counter.builder("outbox.published")
                .description("Số sự kiện outbox đã gửi thành công sang Kafka")
                .register(registry);
        this.relayFailed = Counter.builder("outbox.relay.failed")
                .description("Số lượt gửi thất bại, đã tính cả lượt sẽ được thử lại")
                .register(registry);
        this.terminallyFailed = Counter.builder("outbox.failed")
                .description("Số sự kiện đã hết lượt thử và chuyển sang FAILED")
                .register(registry);

        if (properties.isPendingGaugeEnabled()) {
            io.micrometer.core.instrument.Gauge
                    .builder("outbox.pending", dao, OutboxDao::countPending)
                    .description("Số row outbox đang chờ gửi. Lớn hơn 0 kéo dài nghĩa là có row kẹt")
                    .register(registry);
        }
    }

    public void published() {
        published.increment();
    }

    public void relayFailed() {
        relayFailed.increment();
    }

    public void terminallyFailed() {
        terminallyFailed.increment();
    }
}
```

- [ ] **Step 2: Viết test thất bại cho `OutboxRelayService`**

Tạo `utils/src/test/java/vn/iotstar/outbox/OutboxRelayServiceTest.java`:

```java
package vn.iotstar.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock private OutboxDao dao;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ScheduledExecutorService retryScheduler;
    @Mock private OutboxMetrics metrics;

    private OutboxRelayService relayService;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties();
        relayService = new OutboxRelayService(dao, kafkaTemplate, retryScheduler, metrics, properties);
    }

    private OutboxRecord record(int attempts, Instant nextAttemptAt) {
        return new OutboxRecord(id, "send-email.v1", "user-1", "{\"otp\":\"1\"}",
                attempts, nextAttemptAt);
    }

    @Test
    void nhanViecThatBaiThiKhongGuiKafka() {
        // Pod khác đã giành được row — đây là đường chạy bình thường khi có 2 replica,
        // không phải lỗi, nên tuyệt đối không được gửi trùng.
        when(dao.claimById(id)).thenReturn(Optional.empty());

        relayService.relay(id);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void guiThanhCongThiXoaRow() {
        when(dao.claimById(id)).thenReturn(Optional.of(record(1, Instant.now().plusSeconds(30))));
        when(kafkaTemplate.send("send-email.v1", "user-1", "{\"otp\":\"1\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayService.relay(id);

        verify(dao).delete(id);
        verify(metrics).published();
        verify(retryScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void guiHongVaConLuotThiGiuPendingVaHenThuLai() {
        Instant nextAttempt = Instant.now().plus(45, ChronoUnit.SECONDS);
        when(dao.claimById(id)).thenReturn(Optional.of(record(3, nextAttempt)));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka sập")));

        relayService.relay(id);

        verify(dao).recordError(eq(id), contains("kafka sập"));
        verify(dao, never()).markFailed(any(), any());
        verify(retryScheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(metrics).relayFailed();
    }

    @Test
    void hetLuotThuThiChuyenSangFailedVaKhongHenLai() {
        when(dao.claimById(id)).thenReturn(Optional.of(record(10, Instant.now().plusSeconds(900))));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka sập")));

        relayService.relay(id);

        verify(dao).markFailed(eq(id), contains("kafka sập"));
        verify(dao, never()).recordError(any(), any());
        verify(retryScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        verify(metrics).terminallyFailed();
    }

    @Test
    void sendKhongNhanViecLaiVaoLanNua() {
        // Đường catch-up đã nhận việc bằng chính câu UPDATE của claimBatch. Nếu send() gọi
        // claimById lần nữa thì attempts bị tăng hai lần cho cùng một lượt gửi.
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayService.send(record(1, Instant.now().plusSeconds(30)));

        verify(dao, never()).claimById(any());
        verify(dao).delete(id);
    }
}
```

- [ ] **Step 3: Chạy test để xác nhận fail**

Run: `./mvnw test -pl utils -Dtest=OutboxRelayServiceTest`
Expected: FAIL — lỗi biên dịch, không tìm thấy `OutboxRelayService`

- [ ] **Step 4: Viết `OutboxRelayService`**

```java
package vn.iotstar.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chuyển một bản ghi outbox đã commit sang Kafka.
 * <p>
 * Tách làm hai bước để đường notification và đường catch-up dùng chung phần gửi mà mỗi lượt gửi
 * chỉ nhận việc đúng một lần: {@link #relay(UUID)} tự nhận việc rồi gọi {@link #send(OutboxRecord)};
 * còn catch-up đã nhận việc bằng chính câu {@code claimBatch} nên gọi thẳng {@code send}.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxDao dao;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ScheduledExecutorService retryScheduler;
    private final OutboxMetrics metrics;
    private final OutboxProperties properties;

    /** Đường đi khi nhận được notification: phải tự tranh quyền xử lý trước. */
    public void relay(UUID id) {
        dao.claimById(id).ifPresent(this::send);
    }

    /**
     * Gửi một row đã được nhận việc. Không {@code .get()} ở đâu cả — bản cũ chặn tới 10 giây mỗi
     * event ngay bên trong một transaction bọc cả vòng lặp, đủ để cạn connection pool khi Kafka treo.
     */
    public void send(OutboxRecord record) {
        kafkaTemplate.send(record.topic(), record.key(), record.payload())
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        dao.delete(record.id());
                        metrics.published();
                        log.debug("Đã gửi outbox: id={}, topic={}", record.id(), record.topic());
                    } else {
                        onFailure(record, throwable);
                    }
                });
    }

    private void onFailure(OutboxRecord record, Throwable throwable) {
        metrics.relayFailed();
        String message = truncate(String.valueOf(throwable.getMessage()));

        if (record.attempts() >= properties.getMaxAttempts()) {
            dao.markFailed(record.id(), message);
            metrics.terminallyFailed();
            log.error("Outbox hỏng vĩnh viễn sau {} lượt: id={}, topic={}, lỗi={}",
                    record.attempts(), record.id(), record.topic(), message);
            return;
        }

        dao.recordError(record.id(), message);

        // next_attempt_at đã được câu lệnh nhận việc đẩy về tương lai, nên chỉ cần hẹn đúng mốc đó.
        // Đây là hẹn giờ một phát cho một event cụ thể, không phải vòng quét bảng.
        long delayMillis = Math.max(0, Duration.between(Instant.now(), record.nextAttemptAt()).toMillis());
        retryScheduler.schedule(() -> relay(record.id()), delayMillis, TimeUnit.MILLISECONDS);

        log.warn("Gửi outbox hỏng, sẽ thử lại sau {}ms: id={}, lượt={}, lỗi={}",
                delayMillis, record.id(), record.attempts(), message);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
```

- [ ] **Step 5: Chạy test để xác nhận pass**

Run: `./mvnw test -pl utils -Dtest=OutboxRelayServiceTest`
Expected: PASS, 5 tests

- [ ] **Step 6: Commit**

```bash
git add utils/src/main/java/vn/iotstar/outbox/ utils/src/test/java/vn/iotstar/outbox/
git commit -m "$(cat <<'EOF'
feat(outbox): OutboxRelayService gửi bất đồng bộ, có backoff và trần số lượt

Tách relay(id) khỏi send(record) để đường catch-up không nhận việc hai lần cho
cùng một lượt gửi. Không còn .get() chặn trong transaction như bản cũ.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `OutboxCatchUp` — quét row mồ côi, không định kỳ

**Files:**
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxCatchUp.java`
- Test: `utils/src/test/java/vn/iotstar/outbox/OutboxCatchUpTest.java`

**Interfaces:**
- Consumes: `OutboxDao.claimBatch(int)` (Task 3), `OutboxRelayService.send(OutboxRecord)` (Task 5)
- Produces: `OutboxCatchUp.run()` trả về `int` — số row đã nhặt được, dùng cho log và cho response của endpoint ở Task 7

- [ ] **Step 1: Viết test thất bại**

```java
package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCatchUpTest {

    @Mock private OutboxDao dao;
    @Mock private OutboxRelayService relayService;

    private OutboxRecord record() {
        return new OutboxRecord(UUID.randomUUID(), "t.v1", "k", "{}", 1,
                Instant.now().plusSeconds(60));
    }

    @Test
    void khongCoRowMoCoiThiKhongGuiGiCa() {
        when(dao.claimBatch(anyInt())).thenReturn(List.of());

        int nhatDuoc = new OutboxCatchUp(dao, relayService, new OutboxProperties()).run();

        assertThat(nhatDuoc).isZero();
        verifyNoInteractions(relayService);
    }

    @Test
    void quetTiepLoSauKhiLoDayVaDungKhiLoVoi() {
        OutboxProperties properties = new OutboxProperties();
        properties.setBatchSize(3);
        List<OutboxRecord> loDay = IntStream.range(0, 3).mapToObj(i -> record()).toList();
        // Lô thứ hai chỉ có 1 row, ít hơn batchSize, nên vòng lặp dừng ngay — đúng 2 lần gọi DB.
        // Stub thêm một lô rỗng thứ ba sẽ bị Mockito strict stubbing báo lỗi vì không bao giờ dùng tới.
        when(dao.claimBatch(3)).thenReturn(loDay, List.of(record()));

        int nhatDuoc = new OutboxCatchUp(dao, relayService, properties).run();

        assertThat(nhatDuoc).isEqualTo(4);
        verify(dao, times(2)).claimBatch(3);
        verify(relayService, times(4)).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void goiThangSendChuKhongGoiRelayDeKhongNhanViecHaiLan() {
        OutboxRecord row = record();
        when(dao.claimBatch(anyInt())).thenReturn(List.of(row));

        new OutboxCatchUp(dao, relayService, new OutboxProperties()).run();

        verify(relayService).send(row);
        verify(relayService, org.mockito.Mockito.never())
                .relay(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `./mvnw test -pl utils -Dtest=OutboxCatchUpTest`
Expected: FAIL — không tìm thấy `OutboxCatchUp`

- [ ] **Step 3: Viết `OutboxCatchUp`**

```java
package vn.iotstar.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Nhặt những row mà không pod nào còn nhớ tới — hậu quả của việc một pod chết sau khi COMMIT
 * nhưng trước khi Kafka ack, hoặc của notification bị mất trong lúc connection LISTEN đứt.
 * <p>
 * Cố ý KHÔNG có {@code @Scheduled}. Chỉ chạy ở ba thời điểm: lúc khởi động, sau mỗi lần kết nối
 * lại thành công (xem {@link OutboxNotificationListener}), và khi được gọi tay qua
 * {@link OutboxCatchUpController}.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxCatchUp {

    private final OutboxDao dao;
    private final OutboxRelayService relayService;
    private final OutboxProperties properties;

    /** @return tổng số row đã nhận việc và đưa đi gửi */
    public int run() {
        int tong = 0;
        while (true) {
            List<OutboxRecord> lo = dao.claimBatch(properties.getBatchSize());
            if (lo.isEmpty()) {
                break;
            }
            // Gọi thẳng send: câu claimBatch ở trên CHÍNH LÀ bước nhận việc. Đi qua relay(id)
            // sẽ nhận việc lần nữa và tăng attempts hai lần cho cùng một lượt gửi.
            lo.forEach(relayService::send);
            tong += lo.size();

            if (lo.size() < properties.getBatchSize()) {
                break;
            }
        }

        if (tong > 0) {
            log.info("Catch-up outbox đã nhặt {} row mồ côi", tong);
        }
        return tong;
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `./mvnw test -pl utils -Dtest=OutboxCatchUpTest`
Expected: PASS, 3 tests

- [ ] **Step 5: Commit**

```bash
git add utils/src/main/java/vn/iotstar/outbox/OutboxCatchUp.java utils/src/test/java/vn/iotstar/outbox/OutboxCatchUpTest.java
git commit -m "$(cat <<'EOF'
feat(outbox): OutboxCatchUp nhặt row mồ côi, chạy theo sự kiện chứ không định kỳ

Chỉ chạy lúc khởi động, sau khi kết nối LISTEN được lập lại, và khi gọi tay.
Gọi thẳng relayService.send vì claimBatch đã là bước nhận việc.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: `OutboxNotificationListener`, controller và `OutboxConfiguration`

**Files:**
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxNotificationListener.java`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxCatchUpController.java`
- Create: `utils/src/main/java/vn/iotstar/outbox/OutboxConfiguration.java`
- Test: `utils/src/test/java/vn/iotstar/outbox/OutboxNotificationListenerTest.java`

**Interfaces:**
- Consumes: `OutboxCatchUp.run()` (Task 6), `OutboxRelayService.relay(UUID)` (Task 5), `OutboxProperties` (Task 1)
- Produces: `OutboxConfiguration` — lớp mà `AuthServiceApplication` và `PromotionServiceApplication` sẽ `@Import` ở Task 8 và 9. Bean `outboxKafkaTemplate` kiểu `KafkaTemplate<String, String>`.

- [ ] **Step 1: Viết test thất bại cho vòng đời listener**

```java
package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(MockitoExtension.class)
class OutboxNotificationListenerTest {

    @Mock private OutboxCatchUp catchUp;
    @Mock private OutboxRelayService relayService;

    @Test
    void khongKetNoiDuocThiThuLaiChuKhongChetHan() {
        OutboxProperties properties = new OutboxProperties();
        properties.setReconnectInitialDelay(Duration.ofMillis(20));
        properties.setReconnectMaxDelay(Duration.ofMillis(40));

        OutboxNotificationListener listener = new OutboxNotificationListener(
                "jdbc:postgresql://localhost:1/khong-ton-tai", "u", "p",
                properties, catchUp, relayService);

        listener.start();
        try {
            assertThat(listener.isRunning()).isTrue();
            // Vòng reconnect phải tiếp tục chạy dù mọi lần kết nối đều hỏng.
            await().atMost(Duration.ofSeconds(3))
                    .until(() -> listener.getFailedConnectionAttempts() >= 2);
        } finally {
            listener.stop();
        }

        assertThat(listener.isRunning()).isFalse();
    }
}
```

Test này cần Awaitility. Thêm vào `utils/pom.xml` trong khối `<dependencies>`:

```xml
		<dependency>
			<groupId>org.awaitility</groupId>
			<artifactId>awaitility</artifactId>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `./mvnw test -pl utils -Dtest=OutboxNotificationListenerTest`
Expected: FAIL — không tìm thấy `OutboxNotificationListener`

- [ ] **Step 3: Viết `OutboxNotificationListener`**

```java
package vn.iotstar.outbox;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giữ một connection Postgres riêng ở trạng thái {@code LISTEN} và chuyển mỗi notification thành
 * một lượt gửi.
 * <p>
 * Connection này KHÔNG lấy từ HikariCP: nó bị giữ vô hạn, mượn từ pool sẽ làm đói pool.
 * <p>
 * Vòng lặp chặn ở {@code getNotifications(timeout)}, tức là chặn ở tầng socket — hết timeout mà
 * không có notification thì thức dậy để kiểm tra cờ shutdown và KHÔNG phát câu SQL nào. Đây là
 * điểm khác biệt cốt lõi so với {@code OutboxRelayJob} cũ vốn quét bảng mỗi 5 giây.
 */
@Slf4j
public class OutboxNotificationListener implements SmartLifecycle {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final OutboxProperties properties;
    private final OutboxCatchUp catchUp;
    private final OutboxRelayService relayService;

    private final AtomicInteger failedConnectionAttempts = new AtomicInteger();
    private volatile boolean running;
    private volatile Connection currentConnection;
    private Thread listenerThread;
    private ExecutorService relayExecutor;

    public OutboxNotificationListener(String jdbcUrl, String username, String password,
                                      OutboxProperties properties, OutboxCatchUp catchUp,
                                      OutboxRelayService relayService) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.properties = properties;
        this.catchUp = catchUp;
        this.relayService = relayService;
    }

    public int getFailedConnectionAttempts() {
        return failedConnectionAttempts.get();
    }

    @Override
    public void start() {
        running = true;
        relayExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
        listenerThread = new Thread(this::listenLoop, "outbox-listen");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("Đã khởi động listener outbox trên kênh {}", properties.getChannel());
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(currentConnection);   // phá vỡ lệnh đọc đang chặn ở socket
        if (listenerThread != null) {
            try {
                listenerThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (relayExecutor != null) {
            relayExecutor.shutdown();
        }
        log.info("Đã dừng listener outbox");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listenLoop() {
        long reconnectDelay = properties.getReconnectInitialDelay().toMillis();

        while (running) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                currentConnection = connection;
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + properties.getChannel());
                }

                // Mọi notification phát trong lúc chưa có connection đều đã mất, nên phải quét bù
                // ngay sau khi nối được — kể cả ở lần nối đầu tiên lúc khởi động.
                catchUp.run();
                reconnectDelay = properties.getReconnectInitialDelay().toMillis();

                consumeNotifications(connection.unwrap(PGConnection.class));
            } catch (SQLException e) {
                if (!running) {
                    return;
                }
                failedConnectionAttempts.incrementAndGet();
                log.warn("Mất connection LISTEN outbox, thử lại sau {}ms: {}", reconnectDelay, e.getMessage());
                sleep(reconnectDelay);
                reconnectDelay = Math.min(reconnectDelay * 2, properties.getReconnectMaxDelay().toMillis());
            } finally {
                currentConnection = null;
            }
        }
    }

    private void consumeNotifications(PGConnection pgConnection) throws SQLException {
        int timeoutMillis = (int) properties.getListenTimeout().toMillis();
        while (running) {
            PGNotification[] notifications = pgConnection.getNotifications(timeoutMillis);
            if (notifications == null) {
                continue;   // hết timeout, không có gì — không phát SQL nào
            }
            for (PGNotification notification : notifications) {
                submit(notification.getParameter());
            }
        }
    }

    private void submit(String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            relayExecutor.execute(() -> relayService.relay(id));
        } catch (IllegalArgumentException e) {
            log.warn("Notification outbox mang payload không phải UUID: {}", rawId);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Đóng để phá vỡ lệnh đọc đang chặn; lỗi ở đây không có ý nghĩa gì.
            }
        }
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `./mvnw test -pl utils -Dtest=OutboxNotificationListenerTest`
Expected: PASS, 1 test

- [ ] **Step 5: Viết `OutboxCatchUpController`**

```java
package vn.iotstar.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Chạy tay một vòng catch-up, dùng khi cảnh báo {@code outbox_pending > 0} kéo dài nổ.
 * <p>
 * Không lộ ra ngoài: api-gateway chỉ route {@code /api/v1/**} và {@code /swagger/**}, nên
 * {@code /internal/**} chỉ gọi được từ trong cluster.
 */
@RestController
@RequestMapping("/internal/outbox")
@RequiredArgsConstructor
public class OutboxCatchUpController {

    private final OutboxCatchUp catchUp;

    @PostMapping("/catch-up")
    public ResponseEntity<Map<String, Integer>> chayCatchUp() {
        return ResponseEntity.ok(Map.of("claimed", catchUp.run()));
    }
}
```

- [ ] **Step 6: Viết `OutboxConfiguration`**

```java
package vn.iotstar.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

/**
 * Khai báo toàn bộ bean của cơ chế outbox.
 * <p>
 * Cố ý nằm ngoài {@code vn.iotstar.utils} và không mang stereotype nào để không bị component scan
 * nhặt phải: mọi service trong repo đều quét {@code vn.iotstar.utils}, kể cả bốn service dùng
 * MongoDB vốn không có {@code DataSource} lẫn Kafka. Service muốn dùng thì {@code @Import} lớp này.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    public OutboxDao outboxDao(DataSource dataSource) {
        return new OutboxDao(new JdbcTemplate(dataSource));
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(OutboxDao dao, ObjectMapper objectMapper) {
        return new OutboxEventPublisherImpl(dao, objectMapper);
    }

    /**
     * KafkaTemplate riêng dùng {@link StringSerializer} cho cả key lẫn value.
     * <p>
     * Bắt buộc phải tách khỏi {@code KafkaTemplate<String, Object>} mặc định: payload đã nằm sẵn
     * trong bảng dưới dạng chuỗi JSON, đưa qua {@code JsonSerializer} sẽ bị bọc thêm một lớp
     * nháy kép nữa. Consumer không bị ảnh hưởng vì notification-service và user-service đều truyền
     * thẳng instance {@code new JsonDeserializer<>(type, false)} vào ConsumerFactory, tức bỏ qua
     * header {@code __TypeId__}.
     */
    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties,
                                                             SslBundles sslBundles) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public OutboxMetrics outboxMetrics(MeterRegistry registry, OutboxDao dao,
                                       OutboxProperties properties) {
        return new OutboxMetrics(registry, dao, properties);
    }

    /** Chỉ dùng cho các lượt thử lại một phát của event gửi hỏng — không có tác vụ định kỳ nào. */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService outboxRetryScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "outbox-retry");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public OutboxRelayService outboxRelayService(OutboxDao dao,
                                                 KafkaTemplate<String, String> outboxKafkaTemplate,
                                                 ScheduledExecutorService outboxRetryScheduler,
                                                 OutboxMetrics metrics,
                                                 OutboxProperties properties) {
        return new OutboxRelayService(dao, outboxKafkaTemplate, outboxRetryScheduler, metrics, properties);
    }

    @Bean
    public OutboxCatchUp outboxCatchUp(OutboxDao dao, OutboxRelayService relayService,
                                       OutboxProperties properties) {
        return new OutboxCatchUp(dao, relayService, properties);
    }

    @Bean
    public OutboxNotificationListener outboxNotificationListener(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            OutboxProperties properties, OutboxCatchUp catchUp, OutboxRelayService relayService) {
        return new OutboxNotificationListener(url, username, password, properties, catchUp, relayService);
    }

    @Bean
    public OutboxCatchUpController outboxCatchUpController(OutboxCatchUp catchUp) {
        return new OutboxCatchUpController(catchUp);
    }
}
```

- [ ] **Step 7: Chạy toàn bộ test của utils**

Run: `./mvnw test -pl utils`
Expected: PASS, tất cả test xanh

- [ ] **Step 8: Cài lại utils vào local repo**

Run: `./mvnw -q clean install -pl utils -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add utils/
git commit -m "$(cat <<'EOF'
feat(outbox): listener LISTEN/NOTIFY, endpoint catch-up tay và OutboxConfiguration

Listener giữ connection riêng ngoài HikariCP và chặn ở tầng socket, không phát
SQL nào khi hệ rảnh. KafkaTemplate riêng dùng StringSerializer vì payload đã là
chuỗi JSON sẵn trong bảng.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Chuyển `auth-service` sang outbox dùng chung

**Files:**
- Delete: `auth-service/src/main/java/vn/iotstar/authservice/service/impl/OutboxRelayJob.java`
- Delete: `auth-service/src/main/java/vn/iotstar/authservice/service/impl/EventPublisherImpl.java`
- Delete: `auth-service/src/main/java/vn/iotstar/authservice/service/EventPublisher.java`
- Delete: `auth-service/src/main/java/vn/iotstar/authservice/model/entity/OutboxEvent.java`
- Delete: `auth-service/src/main/java/vn/iotstar/authservice/repository/OutboxEventRepository.java`
- Modify: `auth-service/src/main/java/vn/iotstar/authservice/AuthServiceApplication.java`
- Modify: `auth-service/src/main/java/vn/iotstar/authservice/service/impl/OtpServiceImpl.java:53`
- Modify: `auth-service/src/main/java/vn/iotstar/authservice/service/impl/AuthServiceImpl.java:232`
- Modify: `auth-service/src/test/java/vn/iotstar/authservice/service/impl/AuthServiceImplTest.java:58`

**Interfaces:**
- Consumes: `OutboxEventPublisher` và `OutboxConfiguration` từ Task 4 và 7
- Produces: `auth-service` chạy được, không còn `@Scheduled` liên quan outbox

- [ ] **Step 1: Xoá các lớp cũ**

```bash
git rm auth-service/src/main/java/vn/iotstar/authservice/service/impl/OutboxRelayJob.java \
       auth-service/src/main/java/vn/iotstar/authservice/service/impl/EventPublisherImpl.java \
       auth-service/src/main/java/vn/iotstar/authservice/service/EventPublisher.java \
       auth-service/src/main/java/vn/iotstar/authservice/model/entity/OutboxEvent.java \
       auth-service/src/main/java/vn/iotstar/authservice/repository/OutboxEventRepository.java
```

- [ ] **Step 2: Sửa `AuthServiceApplication`**

Thêm `import org.springframework.context.annotation.Import;` và `import vn.iotstar.outbox.OutboxConfiguration;`, rồi thêm annotation `@Import(OutboxConfiguration.class)`. Giữ nguyên `@EnableScheduling` — `TokenServiceImpl:76` vẫn còn một `@Scheduled` dọn token, không liên quan tới outbox.

Khối annotation sau khi sửa:

```java
@SpringBootApplication
@EnableFeignClients
// Giữ @EnableScheduling: TokenServiceImpl#purgeExpiredTokens vẫn là @Scheduled. Outbox thì không
// còn tác vụ định kỳ nào — nó chạy theo notification của Postgres.
@EnableScheduling
@Import(OutboxConfiguration.class)
@ComponentScan(basePackages = {
        "vn.iotstar.authservice",  // package chính
        "vn.iotstar.utils", // package Utils,
})
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
public class AuthServiceApplication {
```

- [ ] **Step 3: Sửa `OtpServiceImpl`**

Đổi import `vn.iotstar.authservice.service.EventPublisher` thành `vn.iotstar.outbox.OutboxEventPublisher`, đổi khai báo field, và đổi hằng topic sang lớp dùng chung:

```java
import vn.iotstar.outbox.OutboxEventPublisher;
import vn.iotstar.utils.constants.TopicNames;
```

```java
    private final OutboxEventPublisher eventPublisher;
```

Tại dòng 53, đổi:

```java
        eventPublisher.publish(TopicNames.SEND_EMAIL, userId, evt);
```

- [ ] **Step 4: Sửa `AuthServiceImpl`**

Đổi import và field y như bước 3, rồi tại dòng 232:

```java
        eventPublisher.publish(TopicNames.ACTIVATE_ACCOUNT, String.valueOf(user.getId()), userRegister);
```

- [ ] **Step 5: Sửa test đang mock lớp cũ**

Trong `AuthServiceImplTest.java`, đổi dòng 26 và 58:

```java
import vn.iotstar.outbox.OutboxEventPublisher;
```

```java
    @Mock private OutboxEventPublisher eventPublisher;
```

- [ ] **Step 6: Xoá lớp `TopicName` riêng của auth-service nếu không còn ai dùng**

```bash
grep -rn "TopicName\b" --include=*.java auth-service/src | grep -v TopicNames
```

Nếu không còn kết quả nào: `git rm auth-service/src/main/java/vn/iotstar/authservice/util/TopicName.java`
Nếu còn, để nguyên và ghi lại chỗ nào còn dùng.

- [ ] **Step 7: Chạy test của auth-service**

Run: `./mvnw test -pl auth-service`
Expected: PASS, tất cả test xanh

- [ ] **Step 8: Commit**

```bash
git add auth-service/
git commit -m "$(cat <<'EOF'
refactor(auth-service): dùng outbox chung, bỏ OutboxRelayJob quét 5 giây

Xoá bản copy của outbox trong auth-service và chuyển sang vn.iotstar.outbox.
Giữ @EnableScheduling vì TokenServiceImpl vẫn còn một tác vụ định kỳ dọn token.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Chuyển `promotion-service` sang outbox dùng chung

**Files:**
- Delete: `promotion-service/src/main/java/vn/iotstar/promotionservice/service/impl/OutboxRelayJob.java`
- Delete: `promotion-service/src/main/java/vn/iotstar/promotionservice/service/impl/EventPublisherImpl.java`
- Delete: `promotion-service/src/main/java/vn/iotstar/promotionservice/service/EventPublisher.java`
- Delete: `promotion-service/src/main/java/vn/iotstar/promotionservice/model/entity/OutboxEvent.java`
- Delete: `promotion-service/src/main/java/vn/iotstar/promotionservice/repository/OutboxEventRepository.java`
- Modify: `promotion-service/src/main/java/vn/iotstar/promotionservice/PromotionServiceApplication.java`
- Modify: `promotion-service/src/main/java/vn/iotstar/promotionservice/service/impl/CouponServiceImpl.java:189,193`
- Modify: `promotion-service/src/test/java/vn/iotstar/promotionservice/service/impl/CouponServiceImplTest.java:47`
- Modify: `promotion-service/src/test/java/vn/iotstar/promotionservice/service/impl/CouponRedemptionIT.java:74,88-104`

**Interfaces:**
- Consumes: `OutboxEventPublisher` và `OutboxConfiguration` từ Task 4 và 7
- Produces: `promotion-service` chạy được, không còn `@EnableScheduling`

- [ ] **Step 1: Xoá các lớp cũ**

```bash
git rm promotion-service/src/main/java/vn/iotstar/promotionservice/service/impl/OutboxRelayJob.java \
       promotion-service/src/main/java/vn/iotstar/promotionservice/service/impl/EventPublisherImpl.java \
       promotion-service/src/main/java/vn/iotstar/promotionservice/service/EventPublisher.java \
       promotion-service/src/main/java/vn/iotstar/promotionservice/model/entity/OutboxEvent.java \
       promotion-service/src/main/java/vn/iotstar/promotionservice/repository/OutboxEventRepository.java
```

- [ ] **Step 2: Sửa `PromotionServiceApplication`**

Gỡ `@EnableScheduling` cùng import của nó — annotation này chỉ tồn tại để phục vụ `OutboxRelayJob`, và service này không còn `@Scheduled` nào khác (đã kiểm tra bằng `grep -rn "@Scheduled" promotion-service/src`). Thêm `@Import(OutboxConfiguration.class)`.

Khối annotation sau khi sửa:

```java
@SpringBootApplication
@Import(OutboxConfiguration.class)
@ComponentScan(basePackages = {
        "vn.iotstar.promotionservice",  // package chính
        "vn.iotstar.utils", // package Utils,
}, excludeFilters = {
        // AuditAwareImpl của utils đọc danh tính từ claim JWT, còn service này lấy danh tính từ
        // header do gateway inject. Loại ra để chỉ còn đúng một bean AuditorAware (xem
        // JpaAuditingConfig.auditorAware()) — khớp chính xác với MediaServiceApplication.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuditAwareImpl.class),
        // Hai filter dưới đây là mặc định của @SpringBootApplication, nhưng khai báo @ComponentScan
        // tường minh sẽ ghi đè và làm mất chúng. Thiếu TypeExcludeFilter thì các slice test
        // (@WebMvcTest, @DataJpaTest) không lọc được bean và sẽ kéo cả JpaAuditingConfig vào,
        // khiến context không khởi tạo nổi vì thiếu repository/EntityManagerFactory.
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public class PromotionServiceApplication {
```

Thêm `import org.springframework.context.annotation.Import;` và `import vn.iotstar.outbox.OutboxConfiguration;`; xoá `import org.springframework.scheduling.annotation.EnableScheduling;`.

- [ ] **Step 3: Sửa `CouponServiceImpl`**

Đổi import `vn.iotstar.promotionservice.service.EventPublisher` thành `vn.iotstar.outbox.OutboxEventPublisher` và đổi khai báo field ở dòng 46:

```java
    private final OutboxEventPublisher eventPublisher;
```

Hai lời gọi ở dòng 189 và 193 giữ nguyên — chữ ký `publish(String, String, Object)` không đổi.

- [ ] **Step 4: Sửa hai file test**

`CouponServiceImplTest.java` dòng 16 và 47:

```java
import vn.iotstar.outbox.OutboxEventPublisher;
```

```java
    @Mock private OutboxEventPublisher eventPublisher;
```

`CouponRedemptionIT.java`: đổi mọi chỗ nhắc `EventPublisher` sang `OutboxEventPublisher` (import dòng 24, và khối `NoOpEventPublisherConfig` dòng 88-104). Cập nhật luôn comment ở dòng 88-90 cho khớp thực tế mới:

```java
    /**
     * Publish đi qua {@link OutboxEventPublisher} thật sẽ ghi xuống bảng outbox và kích hoạt
     * trigger pg_notify — vượt ra ngoài phạm vi test này, vốn chỉ kiểm tra logic đổi coupon.
     * Mock hẳn publisher để cô lập.
     */
    @TestConfiguration
    static class NoOpEventPublisherConfig {
        @Bean
        OutboxEventPublisher eventPublisher() {
            return org.mockito.Mockito.mock(OutboxEventPublisher.class);
        }
    }
```

Giữ nguyên phần còn lại của lớp test.

- [ ] **Step 5: Chạy test của promotion-service**

Run: `./mvnw test -pl promotion-service`
Expected: PASS, tất cả test xanh

- [ ] **Step 6: Commit**

```bash
git add promotion-service/
git commit -m "$(cat <<'EOF'
refactor(promotion-service): dùng outbox chung, gỡ @EnableScheduling

@EnableScheduling chỉ tồn tại để phục vụ OutboxRelayJob và service này không
còn @Scheduled nào khác, nên gỡ luôn.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Integration test trên Postgres và Kafka thật

**Files:**
- Modify: `auth-service/pom.xml`
- Create: `auth-service/src/test/java/vn/iotstar/authservice/outbox/OutboxIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ package `vn.iotstar.outbox` từ Task 1-7, migration SQL từ Task 2
- Produces: không có (task cuối, chỉ kiểm chứng)

Đây là chỗ duy nhất kiểm chứng được ba thứ mà mock không tái hiện nổi: ngữ nghĩa `RETURNING`, `SKIP LOCKED`, và trigger `pg_notify`.

**Đọc trước khi làm task này:** repo đã có idiom sẵn cho Testcontainers ở `promotion-service/src/test/java/vn/iotstar/promotionservice/service/impl/CouponRedemptionIT.java:54-85`. Testcontainers 1.21.0 (do Spring Boot 3.5.0 BOM ghim) yêu cầu Docker API 1.32 trong khi Docker Engine trên máy phát triển chỉ chấp nhận API ≥1.40, nên **test này sẽ tự bỏ qua khi chạy cục bộ** và chỉ thực sự chạy trên CI. Bám đúng idiom `@EnabledIf("dockerAvailable")` + `@ServiceConnection` của file đó.

Vì không chạy được cục bộ, phần kiểm chứng thật của Task 2 (chạy migration và trigger bằng `docker compose exec psql`) là chỗ duy nhất xác nhận SQL đúng trên máy phát triển. Đừng bỏ qua nó.

Test cố ý **không** boot toàn bộ `auth-service`: dùng một `@SpringBootApplication` tối giản chỉ để kích hoạt autoconfiguration của DataSource, Kafka, Jackson và Micrometer. Boot cả app sẽ kéo theo Redis, khoá RSA và Feign client — toàn những thứ không liên quan tới điều đang kiểm chứng, và mỗi thứ là một cách test đỏ vì lý do sai.

- [ ] **Step 1: Thêm Testcontainers vào `auth-service/pom.xml`**

Chèn vào khối `<dependencies>`, theo đúng cách `media-service` và `notification-service` đang khai báo:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Viết integration test**

```java
package vn.iotstar.authservice.outbox;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import vn.iotstar.outbox.OutboxCatchUp;
import vn.iotstar.outbox.OutboxConfiguration;
import vn.iotstar.outbox.OutboxDao;
import vn.iotstar.outbox.OutboxEventPublisher;
import vn.iotstar.outbox.OutboxRecord;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(classes = OutboxIntegrationTest.OutboxTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("dockerAvailable")
class OutboxIntegrationTest {

    private static final String TOPIC = "outbox-it.v1";

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    /**
     * Context tối giản: chỉ đủ để autoconfiguration dựng DataSource, KafkaTemplate, ObjectMapper
     * và MeterRegistry. Cố ý KHÔNG boot cả auth-service — làm vậy sẽ kéo theo Redis, khoá RSA và
     * Feign client, mỗi thứ là một cách khiến test đỏ vì lý do chẳng liên quan gì tới outbox.
     * Package của lớp này không chứa @Entity nào nên Hibernate khởi tạo với schema rỗng, còn bảng
     * outbox_events do {@link #chuanBiSchema()} tạo.
     */
    @SpringBootApplication
    @Import(OutboxConfiguration.class)
    static class OutboxTestApp {
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = dockerAvailable()
            ? new PostgreSQLContainer<>("postgres:16-alpine")
            : null;

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = dockerAvailable()
            ? new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            : null;

    @Autowired private OutboxEventPublisher publisher;
    @Autowired private OutboxDao dao;
    @Autowired private OutboxCatchUp catchUp;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void chuanBiSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS outbox_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    topic VARCHAR(255) NOT NULL,
                    event_key VARCHAR(255),
                    payload JSONB NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    error_message VARCHAR(2000))
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_outbox_due
                    ON outbox_events (next_attempt_at) WHERE status = 'PENDING'
                """);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION outbox_notify() RETURNS trigger AS $$
                BEGIN
                    PERFORM pg_notify('outbox_new', NEW.id::text);
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events");
        jdbcTemplate.execute("""
                CREATE TRIGGER outbox_notify_trg AFTER INSERT ON outbox_events
                    FOR EACH ROW EXECUTE FUNCTION outbox_notify()
                """);
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void commitThiMessageRaKafkaVaRowBiXoa() {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            transactionTemplate.executeWithoutResult(status ->
                    publisher.publish(TOPIC, "key-1", Map.of("xin", "chao")));

            ConsumerRecord<String, String> nhanDuoc = doiMotMessage(consumer);
            assertThat(nhanDuoc.key()).isEqualTo("key-1");
            assertThat(nhanDuoc.value()).isEqualTo("{\"xin\":\"chao\"}");

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> dao.countPending() == 0);
        }
    }

    @Test
    void rollbackThiKhongCoRowVaKhongCoMessage() {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            transactionTemplate.executeWithoutResult(status -> {
                publisher.publish(TOPIC, "key-rollback", Map.of("a", 1));
                status.setRollbackOnly();
            });

            // Đây là hồi quy cho bug của bản cũ: EventPublisherImpl gửi Kafka trước khi commit,
            // nên transaction rollback vẫn để lọt message ra ngoài.
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            assertThat(records.isEmpty()).isTrue();
            assertThat(dao.countPending()).isZero();
        }
    }

    @Test
    void haiLuongCungTranhMotRowThiChiMotLuongThangDuoc() throws InterruptedException {
        UUID id = ghiRowChoDenHan();

        int soLuong = 8;
        CountDownLatch batDau = new CountDownLatch(1);
        CountDownLatch xong = new CountDownLatch(soLuong);
        AtomicInteger soLanThang = new AtomicInteger();

        for (int i = 0; i < soLuong; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    batDau.await();
                    Optional<OutboxRecord> claimed = dao.claimById(id);
                    if (claimed.isPresent()) {
                        soLanThang.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    xong.countDown();
                }
            });
        }

        batDau.countDown();
        assertThat(xong.await(10, TimeUnit.SECONDS)).isTrue();

        // Đây là chốt chặn cho khiếm khuyết replicas: 2 của bản cũ — hai pod cùng quét một tập
        // row và cùng gửi, khiến mỗi email đi hai lần.
        assertThat(soLanThang.get()).isEqualTo(1);
    }

    @Test
    void nhanViecPhaiDayNextAttemptAtVaTangAttempts() {
        UUID id = ghiRowChoDenHan();

        OutboxRecord claimed = dao.claimById(id).orElseThrow();

        assertThat(claimed.attempts()).isEqualTo(1);
        // Lượt thứ nhất: 2^0 × 30 = 30 giây.
        assertThat(claimed.nextAttemptAt()).isAfter(java.time.Instant.now().plusSeconds(25));
        assertThat(dao.claimById(id)).isEmpty();
    }

    @Test
    void catchUpNhatDuocRowMoCoi() {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            // Ghi thẳng bằng SQL, không qua publisher, để không kích hoạt trigger — mô phỏng đúng
            // tình huống pod chết sau COMMIT và không ai còn nhớ tới row này.
            jdbcTemplate.update("""
                    INSERT INTO outbox_events (id, topic, event_key, payload, next_attempt_at)
                    VALUES (?, ?, ?, CAST(? AS jsonb), now() - interval '1 minute')
                    """, UUID.randomUUID(), TOPIC, "key-mo-coi", "{\"b\":2}");

            assertThat(catchUp.run()).isEqualTo(1);
            assertThat(doiMotMessage(consumer).key()).isEqualTo("key-mo-coi");
        }
    }

    private UUID ghiRowChoDenHan() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_events (id, topic, event_key, payload, next_attempt_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), now() - interval '1 minute')
                """, id, TOPIC, "key-tranh", "{\"c\":3}");
        return id;
    }

    private ConsumerRecord<String, String> doiMotMessage(KafkaConsumer<String, String> consumer) {
        long hetHan = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < hetHan) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                return record;
            }
        }
        throw new AssertionError("Không nhận được message nào trên topic " + TOPIC);
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
```

- [ ] **Step 3: Chạy integration test**

Run: `./mvnw test -pl auth-service -Dtest=OutboxIntegrationTest`

Expected trên máy phát triển: **SKIPPED** (5 tests skipped) vì `dockerAvailable()` trả `false` — đúng như `CouponRedemptionIT`. Điều cần khẳng định ở bước này là test **biên dịch được và bị bỏ qua sạch sẽ**, không phải lỗi context.

Expected trên CI có Docker thật: PASS, 5 tests. Lần đầu mất vài phút kéo image.

Nếu môi trường có Docker mà `commitThiMessageRaKafkaVaRowBiXoa` timeout, kiểm tra log xem `OutboxNotificationListener` đã `LISTEN` được chưa — nó chỉ khởi động khi `OutboxConfiguration` được `@Import`.

- [ ] **Step 4: Chạy toàn bộ test của repo**

Run: `./mvnw test`
Expected: PASS, không module nào đỏ. Đặc biệt kiểm tra bốn service MongoDB (`movie-service`, `media-service`, `user-service`, `notification-service`) vẫn khởi tạo được context — đây là bằng chứng cho quyết định đặt code ngoài `vn.iotstar.utils`.

Nếu một trong bốn service đó đỏ với lỗi kiểu `NoSuchBeanDefinitionException: DataSource` hoặc `KafkaTemplate`, nghĩa là có lớp outbox nào đó bị component scan nhặt phải — kiểm tra lại xem toàn bộ package có đúng là `vn.iotstar.outbox` chứ không phải `vn.iotstar.utils.outbox` hay không. Đó là lá chắn duy nhất: `OutboxConfiguration` mang `@Configuration` và `OutboxCatchUpController` mang `@RestController`, cả hai sẽ bị nhặt ngay nếu lọt vào vùng scan.

- [ ] **Step 5: Commit**

```bash
git add auth-service/pom.xml auth-service/src/test/java/vn/iotstar/authservice/outbox/
git commit -m "$(cat <<'EOF'
test(outbox): integration test trên Postgres và Kafka thật

Kiểm chứng ba thứ mock không tái hiện nổi: RETURNING, SKIP LOCKED và trigger
pg_notify. Có hồi quy cho bug gửi Kafka trước commit và cho bug hai replica
cùng gửi một event.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Cập nhật tài liệu vận hành

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-08-01-outbox-listen-notify-design.md`

**Interfaces:**
- Consumes: kết quả của Task 1-10
- Produces: không có

- [ ] **Step 1: Cập nhật mục Auth Flow trong `CLAUDE.md`**

Thay đoạn mô tả outbox hiện tại:

> Email notifications (OTP, password reset) use the **Transactional Outbox pattern**: `auth-service` writes to an `OutboxEvent` table, `OutboxRelayJob` publishes to Kafka, `notification-service` consumes.

bằng:

```markdown
Email notifications (OTP, password reset) use the **Transactional Outbox pattern**, implemented
once in `vn.iotstar.outbox` (module `utils`) and shared by `auth-service` and `promotion-service`.
The publisher only INSERTs a row inside the business transaction; a Postgres `AFTER INSERT` trigger
calls `pg_notify`, which — being transactional — fires only on COMMIT and reaches every pod holding
a `LISTEN` connection. There is no polling job.

Schema for `outbox_events` is **not** managed by `ddl-auto` (partial indexes and triggers are out of
its reach). Run `<service>/src/main/resources/sql/002_outbox_listen_notify.sql` by hand before
deploying.

Recovery for rows orphaned by a pod crash runs at startup and after any reconnect, never on a timer.
Alert on `outbox_pending > 0` sustained for 5 minutes, then `POST /internal/outbox/catch-up`.
```

Cố ý **không** tạo file alert rule cho Grafana trong task này: `docker-compose/observability/grafana/` hiện chỉ có `datasource.yml`, chưa có provisioning cho dashboard lẫn alerting. Dựng cả một quy ước provisioning mới là việc riêng, nằm ngoài phạm vi thay outbox. Metric `outbox_pending` đã được phát ra, phần cấu hình cảnh báo để lại cho người vận hành — và ghi rõ trong `CLAUDE.md` để không ai tưởng là đã có sẵn.

- [ ] **Step 2: Ghi phần sai khác vào spec**

Thêm vào cuối `docs/superpowers/specs/2026-08-01-outbox-listen-notify-design.md`:

```markdown
## 10. Sai khác khi triển khai

Hai điểm lệch so với mục 3-5 ở trên, quyết định trong lúc viết plan:

1. **Dùng `JdbcTemplate` thay JPA entity + repository.** Câu `UPDATE ... RETURNING` là trung tâm
   của thiết kế, mà Hibernate 6 từ chối thực thi DML qua `getResultList()` và Spring Data
   `@Modifying` không trả về entity. Đổi lại còn bỏ được yêu cầu sửa `@EntityScan` và
   `@EnableJpaRepositories` ở từng service. INSERT vẫn tham gia transaction của caller vì
   `JdbcTemplate` lấy connection qua `DataSourceUtils`.
2. **Backoff tính trong SQL** (`LEAST(POWER(2, attempts) * 30, 900)`) thay vì ở tầng Java, để chỉ
   có một nguồn sự thật; lượt thử lại trong bộ nhớ đọc thẳng `next_attempt_at` trả về.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md docs/superpowers/specs/
git commit -m "$(cat <<'EOF'
docs(outbox): cập nhật CLAUDE.md và ghi lại sai khác khi triển khai

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

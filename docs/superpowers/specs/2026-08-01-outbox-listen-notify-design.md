# Thay thế OutboxRelayJob bằng Postgres LISTEN/NOTIFY

Ngày: 2026-08-01
Phạm vi: `utils`, `auth-service`, `promotion-service`

## 1. Bối cảnh

`auth-service` và `promotion-service` đang có hai bản copy y hệt của `EventPublisherImpl` +
`OutboxRelayJob`. Cặp này có bốn khiếm khuyết:

1. **Gửi Kafka trước khi commit.** `EventPublisherImpl.publish()` gọi
   `kafkaTemplate.send(...).get(30s)` ngay bên trong transaction nghiệp vụ. Nếu transaction đó
   rollback sau, event đã bay sang Kafka — mail kích hoạt gửi cho một user không tồn tại. Đây là
   phá vỡ chính cái outbox đang cố bảo vệ.
2. **Job 5s là đường vận chuyển duy nhất cho nhánh dự phòng.** Mọi event gửi hụt phải đợi vòng
   quét tiếp theo, độ trễ 0–5s.
3. **Không có khoá.** `k8s/auth-service/deployment.yaml` khai báo `replicas: 2`; hai pod cùng
   `SELECT ... WHERE status='PENDING' LIMIT 100` trên cùng tập row, không `FOR UPDATE SKIP LOCKED`,
   không ShedLock → mỗi event gửi hai lần.
4. **Transaction dài.** `@Transactional` bọc cả vòng lặp có `.get(10s)` bên trong. Kafka treo là
   một transaction giữ tới 1000 giây, cạn connection pool, chặn autovacuum. Nếu có exception thoát
   ra ngoài `catch`, mọi row đã set `SENT` trong lượt đó rollback theo và bị gửi lại; `attempts++`
   cũng rollback nên `MAX_ATTEMPTS` không bao giờ chạm tới.

Yêu cầu đã chốt với người dùng:

- Không được mất event.
- Không còn `@Scheduled` quét bảng định kỳ.
- Tách code dùng chung, không copy-paste giữa các service.

## 2. Quyết định kiến trúc

**Postgres `LISTEN`/`NOTIFY` làm cơ chế đẩy, thay cho mọi timer.**

Điểm mấu chốt: `pg_notify()` gọi trong trigger là **transactional** — notification chỉ được phát
khi transaction COMMIT. Nghĩa là chính Postgres cung cấp ngữ nghĩa "sau commit", cho **mọi** pod
đang lắng nghe, chứ không riêng pod đã ghi row. Nhờ vậy không cần
`@TransactionalEventListener(AFTER_COMMIT)` trong app: chỉ còn **một** đường đi duy nhất cho cả
trường hợp bình thường lẫn trường hợp pod ghi row đã chết.

Hệ quả: khi hệ thống rảnh, không có câu SQL nào được phát ra. Không timer, không polling.

### Các phương án đã cân nhắc và loại

- **Sweeper `@Scheduled` 60s** (đơn giản hơn, không phải tự quản connection) — loại vì yêu cầu
  không muốn query định kỳ.
- **Debezium CDC** (đảm bảo tốt nhất, app không còn code relay) — loại vì phải thêm Kafka Connect
  vào docker-compose lẫn k8s và bật `wal_level=logical`.

## 3. Vị trí code

Package **`vn.iotstar.outbox`**, nằm trong artifact `utils`.

Cố ý **không** đặt dưới `vn.iotstar.utils`: mọi service trong repo đều
`@ComponentScan(basePackages = {..., "vn.iotstar.utils"})`, kể cả các service dùng MongoDB
(`movie-service`, `media-service`, `user-service`, `notification-service`). Nếu đặt trong vùng
scan đó, các service này sẽ nạp phải bean outbox và chết context vì thiếu `EntityManagerFactory`
lẫn `KafkaTemplate`.

Vì nằm ngoài vùng scan, service muốn dùng phải khai báo tường minh ba thứ:

```java
@Import(OutboxConfiguration.class)
@EntityScan(basePackages = {"vn.iotstar.authservice.model.entity", "vn.iotstar.outbox"})
@EnableJpaRepositories(basePackages = {"vn.iotstar.authservice.repository", "vn.iotstar.outbox"})
```

`utils/pom.xml` thêm ba dependency, tất cả scope `provided` (không transitive, nên không rơi vào
classpath của service không dùng):

- `org.springframework.kafka:spring-kafka`
- `org.postgresql:postgresql` — cần cho `PGConnection#getNotifications`
- `io.micrometer:micrometer-core`

## 4. Schema

`outbox_events` thay đổi so với hiện tại:

| Cột | Thay đổi | Lý do |
|-----|----------|-------|
| `next_attempt_at TIMESTAMPTZ NOT NULL` | **thêm mới** | Mốc backoff, và là điều kiện tranh quyền xử lý giữa các pod |
| `status` | bỏ giá trị `SENT` | Gửi thành công thì **xoá row**, không giữ lại |
| `sent_at` | **bỏ** | Không còn row nào ở trạng thái đã gửi |

Xoá row thay vì đánh dấu `SENT` khiến bảng luôn gần rỗng, index luôn nhỏ, và **triệt tiêu hoàn
toàn** nhu cầu job dọn dẹp row cũ (dòng SQL cleanup đang bị comment ở `create_outbox_events.sql`).
Kafka và distributed tracing đã là bản ghi của những gì đã gửi. Row `FAILED` được giữ lại để điều
tra.

Migration (file SQL mới, thay `create_outbox_events.sql`):

```sql
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
UPDATE outbox_events SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;
DELETE FROM outbox_events WHERE status = 'SENT';
ALTER TABLE outbox_events DROP COLUMN IF EXISTS sent_at;

DROP INDEX IF EXISTS idx_outbox_status;
CREATE INDEX IF NOT EXISTS idx_outbox_due
    ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION outbox_notify() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('outbox_new', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events;
CREATE TRIGGER outbox_notify_trg AFTER INSERT ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION outbox_notify();
```

Cả hai service đang chạy `ddl-auto: update`, vốn không tạo được partial index lẫn trigger. File SQL
này là nguồn sự thật cho schema và phải chạy tay khi triển khai — ghi rõ trong README của service.

## 5. Các thành phần

### `OutboxEventPublisher` (thay `EventPublisher` của từng service)

```java
void publish(String topic, String key, Object payload);
```

Chỉ làm hai việc, đều trong transaction nghiệp vụ của caller: serialize payload thành JSON, INSERT
một row `PENDING` với `next_attempt_at = now()`. **Không chạm Kafka.** Transaction commit hay
rollback thì outbox row đi theo đúng số phận đó — đây là chỗ sửa khiếm khuyết (1).

Trigger ở mục 4 lo phần còn lại: commit xong, Postgres tự phát notification.

### `OutboxNotificationListener`

Một thread nền, khởi động ở `ApplicationReadyEvent`:

1. Mở **connection JDBC riêng**, không lấy từ HikariCP (connection này bị giữ vô hạn, lấy từ pool
   sẽ đói pool). Dựng từ chính `spring.datasource.*` qua `DataSourceProperties`.
2. `LISTEN outbox_new`.
3. Vòng lặp: `PGConnection#getNotifications(30_000)` — chặn ở tầng socket. **Không phát câu SQL
   nào** khi hết timeout mà không có notification; lần thức dậy chỉ để kiểm tra cờ shutdown và
   sức khoẻ connection.
4. Nhận được notification → parse UUID → đẩy sang executor → `OutboxRelayService.relay(id)`.
5. Mất connection → đóng, backoff 1s→30s, kết nối lại, `LISTEN` lại, rồi **chạy catch-up** (mục
   dưới), vì notification phát trong lúc đứt đã mất.

Shutdown: cờ volatile + `connection.close()`, chờ thread thoát tối đa 5s.

### `OutboxCatchUp`

Không có timer. Chỉ chạy ở đúng hai thời điểm: **khởi động** và **sau mỗi lần reconnect thành
công**. Quét theo lô cho tới khi hết row:

```sql
UPDATE outbox_events
SET attempts = attempts + 1, next_attempt_at = now() + interval '60 seconds'
WHERE id IN (
    SELECT id FROM outbox_events
    WHERE status = 'PENDING' AND next_attempt_at <= now()
    ORDER BY created_at LIMIT 100
    FOR UPDATE SKIP LOCKED)
RETURNING *;
```

Một câu duy nhất, vừa nhận việc vừa hoãn. `SKIP LOCKED` xử lý chuyện hai pod cùng khởi động.
Transaction chỉ kéo dài đúng câu UPDATE đó — không có lệnh mạng nào bên trong, sửa khiếm khuyết
(4). Gửi Kafka diễn ra **sau khi** transaction claim đã commit.

Câu UPDATE này **chính là** bước nhận việc, nên các row nó trả về được đưa thẳng vào
`OutboxRelayService.send(...)`, không đi qua `relay(id)` (nếu không `attempts` sẽ bị tăng hai lần
cho cùng một lượt gửi).

### `OutboxRelayService`

Tách làm hai bước để cả đường notification lẫn đường catch-up dùng chung phần gửi, mà mỗi lượt
gửi chỉ nhận việc đúng một lần.

`relay(UUID id)` — dùng cho đường notification:

1. **Nhận việc, nguyên tử:**
   ```sql
   UPDATE outbox_events
   SET attempts = attempts + 1, next_attempt_at = now() + CAST(? AS interval)
   WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= now()
   RETURNING *;
   ```
   Tham số interval là `backoff(attempts_hiện_tại + 1)` tính ở tầng Java. Trả về 0 dòng nghĩa là
   pod khác đã giành được — bỏ qua, không log warning. Đây là chỗ sửa khiếm khuyết (3).

2. Có row → gọi `send(event)`.

`send(OutboxEvent event)` — dùng chung cho cả hai đường, giả định row đã được nhận việc:

1. **Gửi:** `kafkaTemplate.send(topic, key, payload).whenComplete(...)` — non-blocking, không
   `.get()`. Payload gửi thẳng dạng `String` JSON đã lưu, không deserialize thành `Object` rồi
   serialize lại như hiện tại.

2. **Kết quả:**
   - Thành công → `DELETE FROM outbox_events WHERE id = ?`
   - Thất bại và `attempts >= 10` → `status = 'FAILED'`, ghi `error_message`, log ở mức ERROR
   - Thất bại và còn lượt → giữ `PENDING`; `next_attempt_at` đã được đẩy ở bước nhận việc. Đặt một lượt
     thử lại **một lần** trên `ScheduledExecutorService` đúng lúc `next_attempt_at`. Đây là timer
     một phát cho một event cụ thể, không phải vòng quét bảng.

Backoff: `min(2^(attempts-1) × 30s, 15 phút)`. Với `MAX_ATTEMPTS = 10`, tổng cửa sổ chịu lỗi
khoảng 1 tiếng — thay cho 25 giây của bản hiện tại (5 lần × 5 giây), vốn không sống nổi qua một
lần Kafka rolling restart.

## 6. Rủi ro còn lại và cách bù

Bỏ sweeper định kỳ để lại đúng một lỗ: **pod chết trong lúc đang chờ backoff của một event gửi
hụt.** Row đó ở `PENDING` với `next_attempt_at` trong tương lai, lượt thử lại nằm trong bộ nhớ
pod đã chết, và không pod nào nhận được notification mới cho nó. Nó chỉ được nhặt lại ở lần
catch-up kế tiếp — tức khi có pod khởi động lại hoặc mất/nối lại kết nối DB.

Đây là cái giá thật của việc bỏ timer, không che được. Cách bù là **biến hỏng âm thầm thành hỏng
nhìn thấy được**:

- Gauge `outbox.pending` (số row `PENDING`) và `outbox.failed`, cập nhật mỗi lần relay — không
  cần query riêng.
- Counter `outbox.published` / `outbox.relay.failed`.
- Alert Grafana: `outbox_pending > 0` liên tục quá 5 phút, hoặc `outbox_failed > 0`.
- Endpoint `POST /internal/outbox/catch-up` để chạy tay `OutboxCatchUp` khi alert nổ. Đặt trong
  `vn.iotstar.outbox`, đăng ký bởi `OutboxConfiguration` nên service nào `@Import` cũng có. Chỉ
  gọi được từ trong cluster: api-gateway không route `/internal/**` ra ngoài.

Stack Prometheus/Grafana đã có sẵn trong `docker-compose/qa/` nên phần này không thêm hạ tầng.

## 7. Thay đổi ở từng service

**`auth-service`**
- Xoá `OutboxRelayJob`, `EventPublisherImpl`, `EventPublisher`, `OutboxEvent`, `OutboxEventRepository`.
- `OtpServiceImpl` và `AuthServiceImpl` đổi sang inject `OutboxEventPublisher` của `vn.iotstar.outbox`.
- Giữ `@EnableScheduling` — `TokenServiceImpl:76` vẫn còn một `@Scheduled` dọn token, không liên quan.
- Thêm `@Import` + `@EntityScan` + `@EnableJpaRepositories` như mục 3.

**`promotion-service`**
- Xoá đúng bộ tương ứng.
- `CouponServiceImpl` đổi sang `OutboxEventPublisher`.
- **Gỡ `@EnableScheduling`** — nó chỉ tồn tại để phục vụ `OutboxRelayJob`, không còn `@Scheduled`
  nào khác trong service này.
- Thêm ba khai báo như trên. Lưu ý `excludeFilters` sẵn có chỉ loại `AuditAwareImpl`, không ảnh
  hưởng bean outbox.

## 8. Kiểm thử

**Unit (Mockito), trong `utils`:**
- `publish()` INSERT row và **không** gọi `kafkaTemplate` — chốt chặn cho khiếm khuyết (1).
- `relay()` khi claim trả 0 dòng thì không gửi Kafka.
- Backoff tính đúng theo `attempts`, chạm trần 15 phút.
- Gửi thất bại lần thứ 10 → `FAILED`; lần thứ 9 → vẫn `PENDING` và có đặt lượt thử lại.

**Integration (Testcontainers Postgres + Kafka), đặt trong `auth-service`:**
- Transaction nghiệp vụ rollback → không có row outbox nào và không có message nào trên Kafka.
- Transaction commit → message xuất hiện trên Kafka và row bị xoá.
- Trigger phát notification: `LISTEN` trên connection thứ hai, INSERT rồi commit, khẳng định nhận
  được UUID đúng.
- Hai luồng cùng `relay()` một id → đúng một message trên Kafka. Đây là test bắt buộc phải dùng
  Postgres thật, mock không tái hiện được ngữ nghĩa claim.
- `OutboxCatchUp` nhặt được row mồ côi có `next_attempt_at` trong quá khứ.

`auth-service` hiện chưa có Testcontainers; thêm `spring-boot-testcontainers`,
`testcontainers:postgresql`, `testcontainers:kafka` vào `auth-service/pom.xml` scope `test`, theo
đúng cách `media-service` và `notification-service` đang làm.

## 9. Ngoài phạm vi

- Không đụng `payment-service`, `report-service`, `streaming-service` (chưa có outbox).
- Không chuyển các service MongoDB sang outbox.
- Không thêm Debezium.

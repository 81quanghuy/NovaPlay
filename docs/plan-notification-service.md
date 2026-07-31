# Gộp `email-service` vào `notification-service` và đưa lên mức sẵn sàng production

## Context

Hai module đang chồng lấn vai trò và cả hai đều không deploy được:

- **`notification-service` là scaffold rỗng** — đúng 4 file (`NotificationServiceApplication`, 2 yml, 1 test `contextLoads`). Pom khai báo `spring-cloud-stream` nhưng không có binder nào → dependency chết; còn `jib-maven-plugin` với image `novaPlay/...` (viết hoa) mà movie-service đã chủ động gỡ; thiếu actuator/validation/redis/prometheus; `application-dev.yml` không đặt `server.port` nên mặc định 8080, đụng kafka-ui.
- **`email-service` chạy được nhưng chưa hardened** — 1 `@KafkaListener` trên `send-email.v1`, Redis dedup, Thymeleaf, retry 5 + `.DLT`. Nhưng **toàn bộ config runtime (`spring.mail.*`, `spring.kafka.bootstrap-servers`, redis host, `server.port`) không tồn tại ở bất kỳ đâu trong repo** — chúng nằm trong repo `cloud_config_NovaPlay` đã bị bỏ (compose set `SPRING_CONFIG_IMPORT: ""` cho mọi service). Không Dockerfile, không có trong compose, không k8s, không CI, không prometheus job, không metrics/tracing.

Email chỉ là **một kênh** của notification. Cả hai đều là consumer thuần event-driven, không có state xung đột, và notification-service chưa có gì để mất. Hướng đi: gộp về `notification-service` với kiến trúc channel adapter (email + in-app ngay bây giờ, websocket/FCM thêm sau), rồi hardening theo đúng bộ pattern movie-service đã thiết lập ở commit `30483f6`.

**Kết quả mong đợi:** một service duy nhất, chạy được bằng `./mvnw spring-boot:run -pl notification-service -Dspring-boot.run.profiles=dev` mà không cần config server, có REST API in-app cho FE, có Dockerfile/compose/k8s/CI, và có test thật thay vì `contextLoads`.

## Các quyết định thiết kế

| Vấn đề | Quyết định |
|---|---|
| Topic | **Giữ `send-email.v1` nguyên trạng** (không phải sửa auth-service) + thêm `notification.requested.v1` cho producer tương lai. Consume cả hai. |
| `activate-account.v1` | **Có consume** với groupId riêng `notification-service` (offset độc lập với user-service) → welcome email + in-app. Đây là nguồn traffic thật duy nhất cho kênh in-app ở phase 1. |
| Fan-out nhiều kênh | `NotificationChannel` SPI + `NotificationDispatcher` với **dedup key theo từng kênh** → retry chỉ gửi lại kênh đã fail. |
| Idempotency in-app | Chốt ở sink: `_id = <dedupKey>:IN_APP`, `DuplicateKeyException` = thành công. |
| Danh tính (userId vs email) | **Denormalize `userEmail`** lên document. Không cần Feign sang user-service, không sửa gateway. |
| i18n | Chuyển sang `MessageSource` bundle, xoá hằng số VI/EN hardcode. |
| Hằng số topic | Thêm `vn.iotstar.utils.constants.TopicNames`; migrate các service khác ở commit chore riêng. |
| Port | **8900** |

---

## 1. Phẫu thuật module

### 1.1 Giữ git history

Làm thành **2 commit tách biệt** để `git log --follow` còn dùng được:

**Commit A — chỉ di chuyển, không sửa nội dung:**

```bash
git mv email-service/src/main/java/vn/iotstar/emailservice/{config,service,util} \
       notification-service/src/main/java/vn/iotstar/notificationservice/
git mv email-service/src/main/resources/templates \
       notification-service/src/main/resources/templates
git rm -r email-service
```

**Commit B — rename package** `vn.iotstar.emailservice` → `vn.iotstar.notificationservice` và sắp lại vào layout ở §1.3.

Root `pom.xml`: xoá `<module>email-service</module>`.

### 1.2 `notification-service/pom.xml` — viết lại

**Bỏ:** `spring-cloud-stream` (không binder, và cả repo dùng `spring-kafka` + `@KafkaListener`), `spring-cloud-starter-config`, `spring-messaging`, `jib-maven-plugin`.

**Thêm:** `spring-kafka`, `spring-boot-starter-mail`, `-thymeleaf`, `-validation` (khai báo tường minh — không dựa vào dependency khác tình cờ kéo Hibernate Validator), `-security`, `-data-redis`, `-actuator`, `micrometer-registry-prometheus`, `springdoc-openapi-starter-webmvc-ui:2.8.9`, và `lombok` scope `annotationProcessor`.

**Không thêm `micrometer-tracing-bridge-otel`** — trace do OTel javaagent sinh; có cả hai sẽ sinh span trùng (xem comment trong `movie-service/pom.xml:87`).

**Test:** `spring-boot-testcontainers`, `testcontainers:mongodb`, `testcontainers:kafka`, `testcontainers:junit-jupiter`, `spring-security-test`, `com.icegreen:greenmail-junit5`.

**Build:** `spring-boot-maven-plugin` exclude `spring-boot-devtools`; `maven-compiler-plugin` với annotationProcessorPath lombok; **`maven-failsafe-plugin`** bind `integration-test` + `verify` để `*IT` chỉ chạy ở `mvn verify` (CI) còn `mvn test` vẫn nhanh và không cần Docker.

### 1.3 Layout package

```
vn/iotstar/notificationservice/
├── NotificationServiceApplication.java
├── channel/
│   ├── NotificationChannel.java          (SPI)
│   ├── ChannelRoutingPolicy.java
│   └── impl/{EmailChannel, InAppChannel}.java
├── dispatch/NotificationDispatcher.java
├── consumer/{NotificationEventConsumer, DeadLetterConsumer}.java
├── config/
│   ├── messages/KafkaConsumerConfig.java
│   ├── security/{SecurityConfig, GatewayAuthFilter, HeaderAuthenticationFilter,
│   │             FilterRegistrationConfig, RestAuthenticationHandlers}.java
│   ├── MongoAuditingConfig.java
│   ├── MongoIndexInitializer.java
│   ├── health/MongoIndexHealthIndicator.java
│   ├── observability/NotificationMetrics.java
│   ├── i18n/MessageSourceConfig.java
│   └── client/OpenApiConfig.java
├── controller/NotificationController.java
├── exception/NotificationServiceExceptionHandler.java
├── mapper/NotificationMapper.java
├── model/{entity/Notification, enums/*, dto/*}
├── repository/{NotificationRepository, NotificationRepositoryCustom+Impl}
├── service/{DedupStore, MailSender, NotificationQueryService(+impl), AuditLogger}.java
└── util/{PageableFactory, Constants, MailTemplates}.java
```

### 1.4 App class

Copy đúng pattern `MovieServiceApplication`: `@ComponentScan(basePackages = {"vn.iotstar.notificationservice", "vn.iotstar.utils"})` với `excludeFilters` cho **`AuditAwareImpl`** (nó đọc JWT claim, ở đây danh tính đến từ header gateway) **và `TypeExcludeFilter` + `AutoConfigurationExcludeFilter`** — hai filter này ngầm định trong `@SpringBootApplication` nhưng **mất khi khai báo `@ComponentScan` tường minh**; không có chúng thì slice test kéo theo `MongoIndexInitializer` và không start được. `email-service` hiện thiếu cả ba.

---

## 2. Channel SPI và dispatcher

### 2.1 Interface

```java
public interface NotificationChannel {
    ChannelType type();
    void send(NotificationRequest request);   // ném RuntimeException nếu thất bại
}
```

`ChannelType` enum: `EMAIL, IN_APP, WEBSOCKET, PUSH` (hai cái sau khai báo trước, chưa có impl).

`NotificationRequest` (internal record, không phải DTO Kafka): `dedupKey, messageId, userId, userEmail, NotificationType type, Locale locale, Map<String,String> variables`.

### 2.2 Routing policy

`ChannelRoutingPolicy.channelsFor(NotificationType)`:

| Type | Kênh |
|---|---|
| `OTP` | `EMAIL` |
| `ACCOUNT_ACTIVATED` | `EMAIL`, `IN_APP` |
| `PASSWORD_RESET` | `EMAIL` |
| khác | mặc định `IN_APP` |

OTP **cố tình không vào in-app** — đặt mã OTP vào feed đọc được là lỗ bảo mật.

Với topic generic `notification.requested.v1`, event có thể tự khai báo `Set<String> channels`; `null` thì rơi về policy.

### 2.3 Semantics thất bại một phần — điểm khó nhất

Kafka là at-least-once. Nếu email gửi xong mà in-app fail, retry record **không được gửi lại email**.

`NotificationDispatcher.dispatch(request)`:

1. Với mỗi `channel` trong routing set:
   - key = `notification-service:dedup:<dedupKey>:<CHANNEL>`
   - `if (!dedupStore.acquireOnce(key, TTL))` → skip, `metrics.channelSkippedDuplicate(channel)`, sang kênh kế
   - `try { channel.send(request); metrics.channelSent(channel); }`
   - `catch (RuntimeException e) { dedupStore.release(key); failures.add(channel, e); metrics.channelFailed(channel); }` — **không rethrow ngay**, phải thử hết các kênh còn lại
2. Sau vòng lặp: nếu `failures` không rỗng → `throw new ChannelDeliveryException(failures)` (retryable)

Kafka retry → các kênh đã thành công bị dedup chặn, chỉ kênh fail được gửi lại. Hết 5 lần → `.DLT`.

Lỗi validation ném `IllegalArgumentException` **trước** khi vào dispatcher → non-retryable → đi thẳng DLT.

### 2.4 Quy tắc release cho kênh email

Code hiện tại release dedup key trên **mọi** exception. Nếu SMTP đã nhận mail rồi mới lỗi ở bước sau, người dùng nhận tới 6 email.

**Quy tắc:** giữ claim nếu `mailSender.send(msg)` đã return. Cụ thể trong `EmailChannel`:

- Lỗi khi render template / build `MimeMessage` → chưa gửi → **release**
- `mailSender.send()` ném → JavaMail ném ở tầng transport trước khi server accept → **release**
- Bất cứ gì ném **sau khi** `send()` return → **không release**, chỉ log ERROR

### 2.5 Idempotency in-app

Không cần Redis cho kênh này: `_id = <dedupKey>:IN_APP`. `DuplicateKeyException` khi insert = đã ghi rồi = thành công. Dedup Redis ở dispatcher vẫn giữ làm lớp nhanh, Mongo `_id` là lớp thật.

---

## 3. Event và topic

### 3.1 Không sửa auth-service

`send-email.v1` giữ nguyên payload `EmailOtpRequested(messageId, userId, email, variables)`. Zero coordination. `verify-otp.v1` là hằng số chết ở cả auth-service và email-service — xoá.

### 3.2 Hằng số topic về `utils`

Thêm `utils/src/main/java/vn/iotstar/utils/constants/TopicNames.java` với `SEND_EMAIL`, `ACTIVATE_ACCOUNT`, `SEND_STATUS_MEDIA`, `NOTIFICATION_REQUESTED`, `DLT_SUFFIX`. Hiện mỗi service tự nhân bản `util/TopicName.java` (auth, email, user, media).

`utils` giữ version `0.0.1` — **không bump** — nên chỉ cần `./mvnw install -pl utils` trước khi build service khác. Migrate 4 service kia sang `TopicNames` ở một commit chore riêng, không nhét vào việc này.

### 3.3 Topic generic mới

`utils/.../dto/NotificationRequested.java`:

```java
public record NotificationRequested(
    String messageId, String userId, String userEmail,
    String type, Set<String> channels, String locale,
    Map<String, String> variables) {}
```

Khai báo `NewTopic` cho `notification.requested.v1` (3 partition) + `.DLT` trong `KafkaConsumerConfig`. Chưa có producer nào ở phase 1 — đó là chủ ý, nó là điểm mở rộng cho payment/promotion sau này.

### 3.4 Vấn đề dedup key cho `activate-account.v1`

`UserRegister(username, email)` **không có `messageId`**. Cách tính `dedupKey`, theo thứ tự:

1. `messageId` nếu có (`send-email.v1`, `notification.requested.v1`)
2. Khoá nghiệp vụ tự nhiên — `activate-account:<email>` cho `activate-account.v1` (welcome email chỉ đúng một lần trên đời mỗi user; outbox của auth-service có thể publish lại và `topic:partition:offset` sẽ khác nhau)
3. Cuối cùng mới `topic:partition:offset` từ `@Header(KafkaHeaders.RECEIVED_TOPIC/RECEIVED_PARTITION/OFFSET)` — giống `EventDedupStore` của user-service

Cách này sửa luôn bug hiện tại: fallback `"mid:" + userId + ":" + System.currentTimeMillis()` sinh key mới mỗi lần redeliver nên dedup thành no-op.

`UserRegister` không có `userId` → document in-app để `userId = null`, truy vấn dựa trên `userEmail` (xem §4).

---

## 4. Domain in-app

### 4.1 Document

`model/entity/Notification.java`, `@Document("notifications")`:

| Field | Ghi chú |
|---|---|
| `@Id String id` | `<dedupKey>:IN_APP` — đây là cơ chế idempotency |
| `String messageId` | có thể null |
| `String userId` | có thể null (`UserRegister` không mang userId) |
| `String userEmail` | **bắt buộc** — khoá truy vấn |
| `NotificationType type` | |
| `String title`, `String body` | đã render sẵn theo locale lúc consume |
| `Map<String,String> data` | deep link, entity id… |
| `Instant readAt` | `null` = chưa đọc |
| `@CreatedDate Instant createdAt` | |
| `Instant expiresAt` | mốc cho TTL index |

Dùng `@CreatedDate` với `MongoAuditingConfig` đặt trên một `@Configuration` **chứ không phải trên app class** — đặt ở app class thì `mongoAuditingHandler` bị đăng ký trong mọi slice test context và context không start được (đúng lý do movie-service tách ra).

### 4.2 Index

Khai báo trong `MongoIndexInitializer` (ApplicationRunner), copy pattern movie-service: **so khớp index đã tồn tại theo key set, không theo tên**, nếu không lần restart thứ hai sẽ ăn MongoDB error 85 `IndexOptionsConflict`.

- `idx_user_created`: `{userEmail: 1, createdAt: -1}` — truy vấn list
- `idx_user_unread`: `{userEmail: 1, readAt: 1, createdAt: -1}`, `partialFilterExpression: {readAt: null}` — đếm unread
- `idx_expires_ttl`: `{expiresAt: 1}`, `expireAfterSeconds: 0` — tự dọn

`MongoIndexHealthIndicator` báo DOWN nếu thiếu — và readiness group có `mongoIndex`, nên pod thiếu index sẽ không nhận traffic.

### 4.3 REST API

Base `/api/v1/notifications`. Danh tính lấy từ `X-User-Email` do gateway inject (`HeaderAuthenticationFilter` đưa vào `SecurityContext`), **mọi query/update đều scope theo `userEmail` của principal** — không bao giờ tin userId từ path hay body.

| Method | Path | Ghi chú |
|---|---|---|
| `GET` | `/` | phân trang, `?unreadOnly=true`, trả `GenericResponse` bọc `Page<NotificationDTO>` |
| `GET` | `/unread-count` | |
| `PATCH` | `/{id}/read` | |
| `PATCH` | `/read-all` | |

`markRead` dùng `updateFirst(query(id AND userEmail), set readAt)` và trả **404** khi `matchedCount == 0` — không trả 403, để không tiết lộ notification của người khác có tồn tại.

Phân trang qua `util/PageableFactory` copy từ movie-service: chặn `size` tối đa 100 và whitelist field sort (`createdAt`, `readAt`) — nếu không, client tự truyền field sort tuỳ ý sẽ khiến Mongo full-scan.

---

## 5. Hardening luồng Kafka

`config/messages/KafkaConsumerConfig.java` — port từ email-service kèm các sửa sau:

1. **`dltKafkaTemplate` riêng.** Bug hiện tại: `DeadLetterPublishingRecoverer` nhận `KafkaTemplate<Object,Object>` do Boot autoconfig — template đó dùng `ByteArraySerializer` trong khi consumer đã deserialize thành object → mọi lần publish DLT ném `ClassCastException`, **message hỏng vẫn mất, chỉ khác là có thêm log**. user-service đã gặp và fix bằng bean riêng (`StringSerializer`/`JsonSerializer`, `acks=all`, `enable.idempotence=true`) — xem comment ở [KafkaConsumerConfig.java:83-89](user-service/src/main/java/vn/iotstar/userservice/config/messages/KafkaConsumerConfig.java#L83-L89).
2. **`NewTopic` bean tường minh** cho cả 3 topic + `.DLT` của chúng. email-service hiện không khai báo topic nào, sống nhờ auth-service tạo `send-email.v1` và broker auto-create DLT — nếu broker tắt auto-create thì recoverer không publish được.
3. `setConcurrency(3)` và `MAX_POLL_RECORDS_CONFIG = 200` (email-service không set gì).
4. **Xoá `throw (RuntimeException) ex;`** trong `catch (Exception ex)` — checked exception sẽ ném `ClassCastException` che mất lỗi thật. Consumer để error handler lo retry/DLT, không tự catch (pattern user-service).
5. Dedup key deterministic theo §3.4.
6. Dedup theo từng kênh theo §2.3.
7. Non-retryable: `IllegalArgumentException`, `ResourceNotFoundException`.
8. `auto.offset.reset` — xem §12.

### 5.1 Consumer DLT

Hiện **không có gì trong repo consume bất kỳ `.DLT`** — message vào đó là mất luôn. Phase 1 làm mức tối thiểu, không làm UI re-drive:

`consumer/DeadLetterConsumer` — `@KafkaListener` trên 3 topic `.DLT`, groupId **`notification-service-dlt`**, dùng **container factory riêng không có retry và không có DLT recoverer** (nếu dùng chung factory thì lỗi trong listener DLT sẽ đẩy sang `.DLT.DLT` hoặc loop). Chỉ log ERROR có cấu trúc kèm header `kafka_dlt-exception-message`/`kafka_dlt-original-*` và tăng counter `notification.kafka.event.dlt` để Grafana alert được.

---

## 6. Kênh email — sửa những gì

1. **`expireMinutes` chưa hề được dùng.** [EmailServiceImpl.java:36-38](email-service/src/main/java/vn/iotstar/emailservice/service/impl/EmailServiceImpl.java#L36-L38) nhận tham số nhưng không set vào `Context`, nên template không thể hiện "hết hạn sau N phút". Thêm `context.setVariable("expireMinutes", ...)` và render nó trong `send-otp.html`.
2. **URL frontend hardcode.** `send-otp.html` nhúng cứng `https://localhost:3000/auth-email?email=...`. Chuyển thành `${frontendBaseUrl}` lấy từ `application.frontend.base-url` (env `APP_FRONTEND_BASE_URL`).
3. **i18n.** Bỏ hằng số `MessageProperties.OTP_CODE_VI/_EN` và nhánh `localeTag.startsWith("vi")`. Dùng `MessageSource` với `messages_vi.properties` / `messages_en.properties`, key `mail.otp.subject`, `mail.welcome.subject`. **Gotcha:** để `#{...}` trong template Thymeleaf hoạt động phải khởi tạo `new Context(locale)` — code hiện dùng `new Context()` không locale nên luôn rơi về default.
4. **Template mồ côi.** Chỉ `send-otp.html` được dùng. Giữ `forgot-password.html` (`${url}` — luồng reset password đang có thật ở auth-service). Thêm `welcome.html` cho `activate-account.v1`. **Xoá** `mail-template.html`, `result-confirm.html`, `send-account.html` — không producer, không kế hoạch, và git vẫn giữ lại được nếu cần.
5. **SMTP.** `spring.mail.*` vào `application-dev.yml` (host/port trỏ MailHog hoặc Gmail cá nhân), `application-prod.yml` đọc env. `From` hiện lấy `env.getRequiredProperty("spring.mail.username")` — tách thành `application.mail.from` mặc định về `${spring.mail.username}` để tài khoản SMTP và địa chỉ hiển thị không bị buộc phải giống nhau.

---

## 7. Config

Bám đúng phân lớp movie-service. **Bỏ hẳn `spring.config.import: optional:configserver:...`** ở cả hai file.

**`application.yml`** — chỉ 2 thứ, và **cố tình không đặt `spring.profiles.active`** (nướng profile vào jar khiến build prod chạy config dev nếu ai quên override — notification-service hiện đang mắc lỗi này):

```yaml
spring.application.name: notification-service
logging.pattern.level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
```

Log pattern là bắt buộc: Loki datasource derive link trace bằng regex `\[[^,]+,([0-9a-f]{32}),[0-9a-f]{16}\]`.

**`application-dev.yml`** (`on-profile: dev`): mongo `localhost:27017/notification_service`, redis localhost, kafka `localhost:9092`, mail host local, `server.port: 8900`, `shutdown: graceful`, `application.security.gateway-secret.enabled: false`, eureka localhost:8761, tracing 1.0, `vn.iotstar: DEBUG`.

**`application-prod.yml`** (`on-profile: prod`): mọi giá trị qua env, lettuce pool 16/8/2, `spring.lifecycle.timeout-per-shutdown-phase: 30s`, `gateway-secret.enabled: true`, tracing `${TRACING_SAMPLE_RATE:0.1}`.

Env bắt buộc ở prod: `MONGODB_URI`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `GATEWAY_SHARED_SECRET`, `EUREKA_SERVICE_URL`, `APP_FRONTEND_BASE_URL`.
Env tuỳ chọn: `REDIS_PORT`, `REDIS_PASSWORD`, `MAIL_PORT`, `MAIL_FROM`, `SERVER_PORT`, `TRACING_SAMPLE_RATE`, `NOTIFICATION_TTL_DAYS`.

---

## 8. Security

Copy cả 5 class từ `movie-service/src/main/java/vn/iotstar/movieservice/config/security/`: `SecurityConfig`, `GatewayAuthFilter`, `HeaderAuthenticationFilter`, `FilterRegistrationConfig`, `RestAuthenticationHandlers`.

Ma trận endpoint **đơn giản hơn movie-service** — API ở đây là dữ liệu riêng của từng người, không có phần đọc công khai nào:

- `permitAll`: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/webjars/**`, `/actuator/health`, `/actuator/health/**`, `/actuator/info`
- `anyRequest().authenticated()`

**`/actuator/prometheus` và `/actuator/metrics` cố tình yêu cầu xác thực** (lộ tên endpoint nội bộ và số liệu vận hành) → prometheus job phải mang header, xem §9.

Không được bỏ `FilterRegistrationConfig`: hai filter là `@Component` nên Boot sẽ đăng ký chúng **lần thứ hai** vào servlet chain thô, chạy sai thứ tự trên các path đã permit. Không được bỏ `RestAuthenticationHandlers`: mặc định Spring trả 403 cho request thiếu danh tính và FE sẽ không bao giờ trigger refresh token.

`GatewayAuthFilter` ném `IllegalStateException` ngay ở constructor nếu `enabled=true` mà secret rỗng — giữ nguyên hành vi này.

---

## 9. Observability

**Actuator:** expose `health,info,prometheus,metrics`; `show-details: when-authorized`; `probes.enabled: true`; `management.metrics.tags.application: ${spring.application.name}`.

**Readiness group:** `readinessState, mongo, redis, mongoIndex`. *Không* đưa kafka vào readiness — consumer mất kafka thì không nên bị coi là không sẵn sàng phục vụ REST in-app; kafka lag đã có metric riêng.

**`config/observability/NotificationMetrics.java`** theo pattern `UserMetrics`/`AuthMetrics`:

| Counter | Tag |
|---|---|
| `notification.event.processed` | `topic` |
| `notification.event.failed` | `topic` |
| `notification.event.duplicate` | `topic` |
| `notification.kafka.event.dlt` | `topic` |
| `notification.channel.sent` | `channel` |
| `notification.channel.failed` | `channel` |
| `notification.channel.skipped` | `channel` |
| `notification.email.send` (Timer) | — |

**`service/AuditLogger.java`** theo pattern user-service: dòng `event=... key=value ... traceId=${MDC.get("traceId")}` cho Loki. Email phải đi qua `maskEmail()` — giữ helper hiện có.

**Prometheus:** thêm job vào `docker-compose/observability/prometheus/prometheus.yml`, `metrics_path: /actuator/prometheus`, kèm `http_headers: X-User-Email: ["prometheus-scraper@internal"]` như job user-service (vì actuator đã bật xác thực).

---

## 10. Test

### Unit (surefire, không cần Docker)

| Class | Assert |
|---|---|
| `NotificationDispatcherTest` | fan-out đủ kênh; kênh đã dedup thì skip; **fail một phần → ném retryable và kênh đã thành công không gửi lại ở lượt sau**; tất cả thành công → không ném |
| `ChannelRoutingPolicyTest` | mapping type → channel set; OTP không ra IN_APP; type lạ về mặc định |
| `EmailChannelTest` | mock `JavaMailSender`; subject theo locale vi/en; `expireMinutes` có trong context; `frontendBaseUrl` từ config; **claim không bị release khi `send()` đã return** |
| `InAppChannelTest` | `_id` đúng dạng `<dedupKey>:IN_APP`; `DuplicateKeyException` được coi là thành công |
| `NotificationEventConsumerTest` | payload null / thiếu otp / thiếu email → `IllegalArgumentException`; dedupKey tính đúng cho cả 3 topic |
| `NotificationControllerSecurityTest` | `@WebMvcTest` + `@Import` 5 class security + `@TestPropertySource(gateway-secret.enabled=false)`: anonymous → 401, sai gateway secret → 403, đã auth → 200, **user A đọc notification của user B → 404** |
| `PageableFactoryTest` | chặn size > 100; field sort ngoài whitelist bị từ chối |
| `NotificationMapperTest` | entity → DTO, `readAt` null → `read=false` |
| `NotificationServiceApplicationTests` | **không dùng `@SpringBootTest`** — chỉ assert annotation có mặt, đúng như `MovieServiceApplicationTests` |

### Integration (failsafe, `*IT`)

Cả hai gác bằng `@EnabledIf("dockerAvailable")` với `DockerClientFactory.instance().isDockerAvailable()` — máy không có Docker thì skip chứ không làm đỏ build.

- **`NotificationRepositoryIT`** — `@DataMongoTest` + `@Container @ServiceConnection MongoDBContainer("mongo:7.0")`: `MongoIndexInitializer` chạy **hai lần** không lỗi 85; `_id` trùng → `DuplicateKeyException`; TTL index tồn tại đúng `expireAfterSeconds: 0`; truy vấn unread dùng partial index.
- **`NotificationPipelineIT`** — dùng **Kafka Testcontainer**, *không* `@EmbeddedKafka`: các container factory ở đây khai báo `@Value("${spring.kafka.bootstrap-servers}")` tường minh nên đánh nhau với broker nhúng, và version drift của broker nhúng đã từng gây rắc rối cho pattern này. SMTP dùng **GreenMail** (kiểm chứng MIME output thật, không chỉ mock). Kịch bản: publish `EmailOtpRequested` → 1 mail ở GreenMail + 0 document (OTP không vào in-app); publish `UserRegister` → 1 mail + 1 document; publish cùng `messageId` hai lần → vẫn 1 mail; publish payload hỏng → nằm ở `send-email.v1.DLT`.

---

## 11. Artifact triển khai

**`notification-service/Dockerfile`** — copy `movie-service/Dockerfile`, đổi port 8900 và tên jar `notification-service-v0.0.1.jar` (root pom đặt `finalName=${project.artifactId}-v${project.version}`). Giữ: multi-stage `maven:3.9-eclipse-temurin-21` + `COPY . .` (root pom liệt kê mọi module nên Maven đòi tất cả phải hiện diện), `mvn -B -q clean package -pl notification-service -am -DskipTests`, runtime `eclipse-temurin:21-jre-alpine`, `apk add curl`, user non-root `novaplay`, tải OTel agent 2.11.0 về `/app/libs/`, `JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"`, `HEALTHCHECK` gọi `/actuator/health/readiness`.

> **Bug sẵn có cần xử lý:** `docker-compose/qa/common-config.yml` trỏ `JAVA_TOOL_OPTIONS` tới `/app/libs/opentelemetry-javaagent-2.11.0.jar` còn Dockerfile tạo file `/app/libs/opentelemetry-javaagent.jar`. Tên không khớp → agent không load, movie-service cũng đang bị. Xử lý: service mới override `JAVA_TOOL_OPTIONS` trong compose entry của chính nó, và ghi chú lại để fix chung sau.

**compose** (`docker-compose/qa/docker-compose.yml`): entry `<<: *microservice-base`, `depends_on` mongodb/redis/kafka-services điều kiện `service_healthy`, `SERVER_PORT: 8900`, `SPRING_CONFIG_IMPORT: ""`, biến mail đọc từ `.env`, healthcheck curl readiness.

**k8s** `k8s/notification-service/` — 6 manifest mirror movie-service: `configmap`, `deployment` (2 replica, RollingUpdate maxSurge 1/maxUnavailable 0, `terminationGracePeriodSeconds: 45`, annotation prometheus scrape, `SPRING_PROFILES_ACTIVE=prod`, `JAVA_TOOL_OPTIONS=-javaagent:/app/libs/opentelemetry-javaagent.jar`, `OTEL_SERVICE_NAME`, envFrom configMap+secret, requests 512Mi/250m limits 1Gi/1000m, startupProbe 30×5s + liveness + readiness, `runAsNonRoot`/`runAsUser: 1000`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `drop: [ALL]`, emptyDir `/tmp`), `hpa` (2→10 @70% CPU, stabilization 300s), `networkpolicy` (ingress chỉ từ `app: api-gateway` và namespace `monitoring`), `service` (ClusterIP), `secret.example.yaml` (`MAIL_PASSWORD`, `GATEWAY_SHARED_SECRET`, `MONGODB_URI` — không giá trị thật).

**CI** `.github/workflows/notification-service-ci.yml` — copy movie-service: path filter `notification-service/**`, `utils/**`, `pom.xml`, chính workflow; `concurrency` + `cancel-in-progress`; JDK 21 temurin + maven cache; `./mvnw -B -q install -N` rồi `install -pl utils -DskipTests`; `./mvnw -B verify -pl notification-service -am`; upload surefire + failsafe `if: always()` 7 ngày; job `docker-build` riêng.

**Gateway** (`api-gateway/src/main/resources/application-dev.yml`): route `notification-service` với `Path=/api/v1/notifications/**`, filter `CircuitBreaker(name: notification-service-cb)` + `Retry` + `RequestRateLimiter`; thêm instance `notification-service-cb` vào block `resilience4j`; thêm route `notification-service-swagger` với `RewritePath=/swagger/notification/(?<segment>.*), /${segment}`. **Không** thêm vào `PublicEndpoints.java` — API này bắt buộc có JWT.

**Docs:** `CLAUDE.md` cập nhật Service Port Map (thêm `notification-service | 8900 | MongoDB + Redis + Kafka`, xoá mọi tham chiếu email-service); `README.md` bỏ email-service khỏi lệnh chạy local và cập nhật sơ đồ; `docs/smoke-test-notification-service.md` theo cấu trúc 5 phần của `docs/smoke-test-movie-service.md` (startup + health có bước **restart lần hai** để bắt lỗi index; ma trận phân quyền qua gateway; luồng event end-to-end cho cả 3 topic; hành vi dedup và DLT; vận hành — `/actuator/prometheus` không auth → 401, kill Mongo → readiness DOWN nhưng liveness UP, SIGTERM drain, traceId có trong log).

---

## 12. Thứ tự thực thi

Branch: `git branch -m refactor/merge-email-into-notification-service`

| # | Commit | Nội dung |
|---|---|---|
| 0 | — | Tạo `docs/plan-notification-service.md` từ file plan này |
| 1 | `chore(utils)` | `TopicNames`, `NotificationRequested`; `./mvnw install -pl utils` |
| 2 | `refactor(notification-service)` | `git mv` thuần (§1.1 commit A) |
| 3 | `refactor(notification-service)` | rename package, viết lại pom, app class, xoá module email-service khỏi root pom |
| 4 | `feat(notification-service)` | channel SPI + dispatcher + routing policy + `DedupStore` |
| 5 | `feat(notification-service)` | kênh in-app: document, index initializer, repository, service, controller, mapper, DTO |
| 6 | `fix(notification-service)` | hardening Kafka: `dltKafkaTemplate`, `NewTopic`, concurrency, dedup key, bỏ ép kiểu ngoại lệ, consumer DLT |
| 7 | `feat(notification-service)` | kênh email: i18n `MessageSource`, `expireMinutes`, URL config, dọn template |
| 8 | `feat(notification-service)` | security 5 class + exception advice + config 3 file + actuator + metrics |
| 9 | `test(notification-service)` | unit + IT + failsafe |
| 10 | `chore(notification-service)` | Dockerfile, compose, k8s, CI, route gateway |
| 11 | `docs` | CLAUDE.md, README, smoke test |

### Breaking change và điểm cần phối hợp

- **Consumer group cutover — quan trọng nhất.** email-service dùng groupId `email-service`; service mới dùng `notification-service`. Group mới bắt đầu từ `auto.offset.reset`. Đặt `earliest` sẽ **replay toàn bộ OTP lịch sử và spam thật**; đặt `latest` sẽ bỏ các message đang tồn. Quyết định: **`latest`**, và quy trình cutover là *drain email-service về lag 0 rồi mới stop nó, sau đó mới start notification-service*. Ghi bước này vào smoke test doc.
- **Xoá module `email-service`** làm `./mvnw spring-boot:run -pl email-service` hỏng — README đang hướng dẫn lệnh đó.
- **api-gateway** phải restart để nhận route mới.
- **FE `81quanghuy/NovaPlay_FE`**: có API in-app mới để dùng; đồng thời link verify trong email OTP giờ do `APP_FRONTEND_BASE_URL` quyết định chứ không còn hardcode `localhost:3000`.
- **auth-service không phải sửa gì** cho việc này. Có một cải thiện tách riêng, không bắt buộc: `EventPublisherImpl.publish` gọi `kafkaTemplate.send(topic, key, payload)` không set header nào, nên `@Header("correlationId")` phía consumer **luôn null** — thêm header ở một commit độc lập.

### Rủi ro khác đã nhận diện

- MongoDB trong compose chạy replica set 1 node (`--replSet rs0`); nếu chạy Mongo standalone ở nơi khác thì `@Transactional` **âm thầm thành no-op**. Ở đây in-app chỉ insert đơn lẻ nên không phụ thuộc transaction — đừng vô tình đưa transaction vào dispatcher.
- Kafka trong compose có hai listener: `9092` (ngoài) và `29092` (trong network). Config dev dùng `localhost:9092`, compose entry phải dùng `kafka-services:29092`.
- `GenericResponse` chỉ set `timestamp`/`path` ở nhánh lỗi — đừng assert hai field đó ở response thành công trong test.
- Không copy `AuditAwareImpl` từ `utils`: nó đọc JWT claim, còn service này chỉ có header từ gateway → auditor sẽ luôn null. Dùng một `AuditorAware` đọc `SecurityContext` hoặc bỏ hẳn `createdBy`.

---

## Verification

```bash
# 1. utils trước, rồi build + test toàn bộ (surefire + failsafe/Testcontainers)
./mvnw -B -q install -N && ./mvnw -B -q install -pl utils -DskipTests
./mvnw -B verify -pl notification-service -am

# 2. Hạ tầng + chạy service
docker compose -f docker-compose/qa/docker-compose.yml up -d
./mvnw spring-boot:run -pl notification-service -Dspring-boot.run.profiles=dev

# 3. Health và readiness (mongoIndex phải UP)
curl -s localhost:8900/actuator/health/readiness | jq
# Restart lần hai để chắc chắn không có IndexOptionsConflict
```

**End-to-end:** đăng ký user qua gateway (`POST localhost:8072/api/v1/auth/...`) → auth-service publish `send-email.v1` → xác nhận mail đến (MailHog) và log có `traceId`; publish `activate-account.v1` → có thêm 1 document trong `notifications`; gọi `GET localhost:8072/api/v1/notifications` với JWT → thấy đúng notification của mình; gọi bằng JWT của user khác → không thấy.

**Dedup:** publish lại đúng `messageId` bằng kafka-ui → không có mail thứ hai, không có document thứ hai, counter `notification.channel.skipped` tăng.

**DLT:** publish payload hỏng → message xuất hiện ở `send-email.v1.DLT` trong kafka-ui, `DeadLetterConsumer` log ERROR, counter `notification.kafka.event.dlt` tăng.

**Bảo mật/vận hành:** `curl localhost:8900/actuator/prometheus` không auth → 401; gọi trực tiếp `localhost:8900/api/v1/notifications` không qua gateway khi bật gateway-secret → 403; `docker stop mongodb` → readiness DOWN, liveness vẫn UP; `docker build -f notification-service/Dockerfile .` thành công.

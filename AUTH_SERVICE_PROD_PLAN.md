# Auth-Service Production Readiness Plan

## Context

`auth-service` là module xác thực trung tâm của NovaPlay (Spring Boot 3.5, Java 21, PostgreSQL, Redis, Kafka). Hiện đạt ~85% prod-ready với core auth flow đầy đủ (register/OTP/login/refresh/logout/forgot-reset/change password) nhưng còn các lỗ hổng kiến trúc và bảo mật cần khắc phục trước khi go-live.

**Scope chọn:** Full prod-ready (9 phases). Wrap toàn bộ response (breaking change chấp nhận). **Xoá hoàn toàn OAuth2 infrastructure** (Provider entity và toàn bộ liên quan).

**Ước lượng tổng:** 3–4 tuần cho 1 dev.

---

## Phân tích auth-service hiện tại

### Đã có

| Lớp | Component | Trạng thái |
|---|---|---|
| Entity | `User`, `Role`, `Permission`, `Token`, `Provider` | OK (Provider sẽ xoá) |
| Repository | `UserRepository`, `RoleRepository`, `TokenRepository`, `PermissionRepository`, `ProviderRepository` | OK (Provider sẽ xoá) |
| Service interface + impl | `AuthService`, `JwtService`, `OtpService`, `TokenService` | Đầy đủ |
| Service interface KHÔNG impl (dead code) | `UserService`, `RoleService`, `KafkaService` | Sẽ xoá |
| Controller | `AuthController` (8 endpoint), `AdminController` (rỗng) | Cần wrap response, AdminController sẽ xoá |
| Security | `SecurityConfig`, `JwtAuthenticationFilter`, `ApplicationConfig` (UserDetailsService, AuthManager, BCrypt) | OK về chức năng, JWT verification cần fix dùng public key |
| Config | `AuthServiceKeyConfig` (private key), `KafkaConfig` (1 topic), `RedisConfig`, `OpenApiConfig` | Cần thêm public key bean, retry/DLT |
| Util | `Constants`, `MessageProperties`, `RoleName`, `TokenType`, `TopicName` | OK |
| Mapper | `UserMapper`, `RoleMapper`, `PermissionMapper`, `ProviderMapper` (sẽ xoá) | OK |
| DTO | 14 DTOs trong `model/dto/` | `VerifyOtpRequest` thiếu validation; ProviderDTO/RecaptchaResponse sẽ xoá |
| Tests | `AuthServiceImplTest` (11 method), `JwtServiceImplTest` (3), `AuthControllerTest` (2) | Coverage chưa đủ — thiếu test cho register/login/refresh/logout/forgot/activate |

### Cần thêm / sửa

- Typed exception thay `RuntimeException` (`AuthServiceImpl:59`)
- Validation cho `VerifyOtpRequest`
- JWT thêm `iss`, `aud`, `jti`, `kid` claims; verify bằng public key
- Brute-force protection (login + OTP verify) qua Redis counter
- Account state enforcement: reject login khi chưa verify email với message rõ ràng
- Sync gateway whitelist với SecurityConfig PUBLIC_ENDPOINTS
- Kafka producer reliability: idempotence, acks=all, retry, DLT, transactional outbox
- Wrap toàn bộ HTTP response trong `GenericResponse`
- Strong password policy (10 char, complexity)
- `User.roles` chuyển EAGER → LAZY + EntityGraph
- AuditLogger cho security events
- Test coverage ≥80% (Mockito + Testcontainers)
- Observability: Prometheus metrics, health probes, OTel tracing
- JWT blacklist (jti) qua Redis cho logout-immediate
- Xoá toàn bộ OAuth2 code (Provider entity, repo, mapper, DTO, processOAuth2Login)

---

## Phase-by-Phase Plan

### Phase 0 — Cleanup & dead-code removal

**Mục tiêu:** Loại bỏ dead code, xoá hoàn toàn OAuth2 infrastructure, sửa exception types, thêm validation thiếu.

**Files xoá:**
- `service/UserService.java` (interface không impl)
- `service/RoleService.java` (interface không impl)
- `service/KafkaService.java` (interface không impl)
- `controller/admin/AdminController.java` (rỗng)
- `model/entity/Provider.java`
- `repository/ProviderRepository.java`
- `mapper/ProviderMapper.java`
- `model/dto/ProviderDTO.java`
- `model/dto/RecaptchaResponse.java` (không dùng)
- Trường `providers` trong `User.java` (line 66-67) cùng với migration drop bảng `providers` & `provider_user`

**Files sửa:**
- `service/AuthService.java` — xoá method `processOAuth2Login(String, String)`
- `service/impl/AuthServiceImpl.java`:
  - Line 59: `throw new ResourceNotFoundException("Role USER not found - DB seed missing")`
  - Xoá impl của `processOAuth2Login` (line 106-108)
  - Remove import liên quan
- `model/dto/VerifyOtpRequest.java` — thêm `@NotBlank @Email` cho email, `@NotBlank @Pattern(regexp="\\d{6}")` cho otp
- `controller/AuthController.java` — thêm `@Valid` cho `verify`, `resendRegistrationOtp`, `forgotPassword` parameters
- `service/impl/OtpServiceImpl.java` line 38 — `throw new TooManyRequestsException(...)` thay `IllegalStateException`

**Files thêm trong utils module:**
- `utils/.../exceptions/wrapper/TooManyRequestsException.java` (extends RuntimeException)
- `utils/.../exceptions/GlobalExceptionHandler.java` — thêm `@ExceptionHandler(TooManyRequestsException.class)` mapping → `429 Too Many Requests`

**DB migration (Flyway/Liquibase):**
- Drop `providers` table và join table với users
- (Schema drop should be reversible — keep idempotent script)

**Acceptance:**
- `mvn -pl auth-service clean compile` green
- `grep -r "processOAuth2Login\|Provider" auth-service/src/main` → 0 hits
- `grep "RuntimeException(" auth-service/src/main` → 0 hits
- Existing 16 tests still pass
- Tất cả `@RequestBody` có `@Valid`

**Effort:** S (1 day) | **Dependencies:** none

---

### Phase 1 — JWT correctness & key separation

**Mục tiêu:** Sửa lỗ hổng kiến trúc — JWT verify bằng public key (không phải private), thêm standard claims để hỗ trợ blacklist & key rotation tương lai.

**Files sửa:**
- `config/security/AuthServiceKeyConfig.java`:
  - Thêm bean `RSAPublicKey rsaPublicKey()` load từ `classpath:certs/public.pem`
  - Inject `@Value("${auth.jwt.kid:v1}")` cho key ID
- `service/JwtService.java` — thêm `String extractJti(String token)`, `String extractIssuer(String token)`
- `service/impl/JwtServiceImpl.java`:
  - Inject thêm `RSAPublicKey rsaPublicKey`
  - `generateToken`: thêm `setIssuer("${auth.jwt.issuer}")`, `setAudience("${auth.jwt.audience}")`, `setId(UUID.randomUUID().toString())`, `setHeaderParam("kid", kid)`, ký bằng `rsaPrivateKey` (giữ nguyên)
  - `extractEmail`, `isTokenValid`, `extractRoles`: chuyển sang dùng `rsaPublicKey` để verify
- `service/JwtService.java` interface — thêm các method mới
- Cấu hình config server (`auth-service.yml` trên Git config repo):
  ```yaml
  auth:
    jwt:
      issuer: novaplay-auth
      audience: novaplay
      kid: v1
      expiration: 900000          # 15 min
      refresh-token:
        expiration: 2592000000    # 30 days
  ```
- `api-gateway/.../jwt/impl/JwtUtilImpl.java` — verify thêm `iss` matches `novaplay-auth`, `aud` contains `novaplay`; reject nếu không khớp
- `utils/.../jwt/impl/JwtUtilImpl.java` — same iss/aud check (nếu được dùng)

**Tests sửa:**
- `JwtServiceImplTest`: thêm assertions cho `iss`, `aud`, `jti` (unique mỗi lần generate); test verify-with-wrong-key fail
- Thêm test `extractJti_returnsNonNullUuid`

**Acceptance:**
- Decode JWT → header có `kid`, payload có `iss`, `aud`, `jti`, `sub`, `roles`, `iat`, `exp`
- Verify bằng public key thành công; bằng key sai → fail
- Gateway reject token với `iss` sai (integration test)

**Effort:** M (2 days) | **Dependencies:** Phase 0

**Risk:** Access token cũ (không có iss/aud) sẽ invalid sau deploy → TTL 15 phút giúp window ngắn. Document rollout step.

---

### Phase 2 — Account state enforcement & gateway sync

**Mục tiêu:** Reject login khi chưa verify email với message rõ ràng; đồng bộ gateway whitelist với auth-service.

**Files sửa:**
- `model/entity/User.java`:
  - `isEnabled()` return `Boolean.TRUE.equals(isEmailVerified)` (Spring Security throws `DisabledException` cho user chưa enable)
  - `isAccountNonLocked()` return `!Boolean.FALSE.equals(isActive)` (lock khi admin disable account)
- `service/impl/AuthServiceImpl.java::login`:
  - Catch `DisabledException` → throw `ForbiddenException("Account not verified - check your email for OTP")`
  - Catch `LockedException` → throw `ForbiddenException("Account is locked - contact support")`
  - Catch `BadCredentialsException` → throw `BadRequestException("Invalid credentials")` (uniform message để chống enumeration)
- Tạo file mới `api-gateway/.../constants/PublicEndpoints.java`:
  ```java
  public final class PublicEndpoints {
      public static final List<String> AUTH = List.of(
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/verify-otp",
          "/api/v1/auth/resend-registration-otp",
          "/api/v1/auth/forgot-password",
          "/api/v1/auth/reset-password",
          "/api/v1/auth/refresh-token"
      );
  }
  ```
- `api-gateway/.../filters/AuthenticationFilter.java` — sử dụng `PublicEndpoints.AUTH` thay hardcode list (line 39-44)
- `auth-service/.../config/security/SecurityConfig.java` — refactor PUBLIC_ENDPOINTS sang đọc cùng nguồn (option: nhân bản constant trong utils, hoặc giữ 2 list nhưng có comment cross-reference)

**Tests:**
- `AuthServiceImplTest::login_unverifiedEmail_throwsForbidden`
- `AuthServiceImplTest::login_invalidPassword_throwsBadRequest`
- `AuthServiceImplTest::login_lockedAccount_throwsForbidden`
- Integration test gateway: gọi `/resend-registration-otp` không token → 200 (currently 401)

**Acceptance:**
- Register → login (chưa verify) → 403 với message clear
- 6 endpoint public ở SecurityConfig matches gateway whitelist
- Brute force username enumeration không tồn tại (login fail = same message)

**Effort:** S (1 day) | **Dependencies:** Phase 0

---

### Phase 3 — Brute-force protection (login + OTP verify)

**Mục tiêu:** Chặn credential stuffing và OTP brute-force qua Redis counter, tái sử dụng pattern `OtpServiceImpl` rate limit.

**Files thêm:**
- `service/RateLimiterService.java` (interface):
  ```java
  void checkAndIncrement(String key, int max, Duration window) throws TooManyRequestsException;
  void reset(String key);
  long getCurrentCount(String key);
  ```
- `service/impl/RateLimiterServiceImpl.java` — dùng `StringRedisTemplate`, logic giống `OtpServiceImpl:35-39`

**Files sửa:**
- `service/impl/AuthServiceImpl.java::login`:
  - Đầu method: `rateLimiter.checkAndIncrement("auth:login:fail:" + emailOrUsername, 5, Duration.ofMinutes(15))`
  - Try authenticate; on success → `rateLimiter.reset(...)`; on fail → exception đã propagate (counter đã tăng từ trước)
  - Hoặc reverse: try authenticate; on `BadCredentialsException` → increment then rethrow
- `service/impl/OtpServiceImpl.java::verify`:
  - Đầu method: `rateLimiter.checkAndIncrement("auth:otp:verify:" + email, 10, Duration.ofMinutes(15))`
  - On `ok=true`: `rateLimiter.reset(...)`
- Refactor `OtpServiceImpl::generateAndDispatch` send rate limit (line 35-39) sang dùng `rateLimiter` để DRY

**Tests:**
- `RateLimiterServiceImplTest` (Testcontainers Redis)
- `AuthServiceImplTest::login_after5Failures_throws429`
- `AuthServiceImplTest::login_successResetsCounter`
- `OtpServiceImplTest::verify_after10Failures_throws429`

**Acceptance:**
- 6 login fails / 15 min → 429
- 11 OTP verify fails / 15 min → 429
- Login success reset counter ngay lập tức

**Effort:** M (2 days) | **Dependencies:** Phase 0 (TooManyRequestsException), Phase 2

---

### Phase 4 — Kafka reliability (idempotence + retry + DLT + outbox)

**Mục tiêu:** Đảm bảo events không mất khi Kafka tạm thời unavailable.

**Files sửa:**
- `config/messages/KafkaConfig.java`:
  - Producer config: `acks=all`, `enable.idempotence=true`, `retries=Integer.MAX_VALUE`, `delivery.timeout.ms=120000`, `linger.ms=5`, `max.in.flight.requests.per.connection=5`
  - `transactional.id-prefix=auth-tx-`
  - Bean `DefaultErrorHandler` với `ExponentialBackOff(initialInterval=1s, multiplier=2, maxInterval=30s)` và `DeadLetterPublishingRecoverer` publish sang `${topic}.DLT`
  - Tạo NewTopic cho `SEND_EMAIL.DLT`, `ACTIVATE_ACCOUNT.DLT`

**Files thêm (transactional outbox pattern):**
- `model/entity/OutboxEvent.java`:
  ```
  id UUID, topic VARCHAR, key VARCHAR, payload JSONB, status (PENDING/SENT/FAILED), 
  attempts INT, createdAt, sentAt, errorMessage
  ```
- `repository/OutboxEventRepository.java` — `findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)`
- `service/EventPublisher.java` (interface) + `service/impl/EventPublisherImpl.java`:
  - `publish(String topic, String key, Object payload)` — INSERT vào `outbox_events` trong cùng transaction với business write
  - Sau commit, fire async send qua Kafka
- `service/impl/OutboxRelayJob.java` — `@Scheduled(fixedDelay=5000)` quét PENDING events, retry send, đánh dấu SENT/FAILED, exponential backoff theo `attempts`

**Files sửa:**
- `service/impl/AuthServiceImpl.java::activateAccount` (line 184) — dùng `eventPublisher.publish(TopicName.ACTIVATE_ACCOUNT, userId, userRegister)` thay `kafkaTemplate.send`
- `service/impl/OtpServiceImpl.java::generateAndDispatch` (line 50) — dùng `eventPublisher.publish(TopicName.SEND_EMAIL, userId, evt)`

**Tests:**
- `EventPublisherImplTest` — verify outbox row được tạo trong cùng transaction
- `OutboxRelayJobTest` — verify retry logic
- Integration test: stop Kafka → call forgot-password → verify outbox row PENDING → start Kafka → verify SENT

**Acceptance:**
- Kafka down → API vẫn 200, event ghi vào outbox
- Kafka up trở lại → relay job pickup và send thành công
- Send fail vĩnh viễn → publish sang DLT

**Effort:** L (4 days) | **Dependencies:** Phase 0

**Risk:** Outbox table grow nhanh → thêm cleanup job xoá events SENT > 7 ngày.

---

### Phase 5 — Response wrapping & API contract

**Mục tiêu:** Wrap toàn bộ HTTP response trong `GenericResponse<T>` (breaking change). Thêm endpoint thiếu cho prod.

**Files sửa:**
- `utils/.../constants/GenericResponse.java` — verify generic `<T>`; thêm static helpers:
  ```java
  public static <T> GenericResponse<T> success(T data, String message);
  public static <T> GenericResponse<T> success(T data);
  public static GenericResponse<Void> ok(String message);
  ```
- `controller/AuthController.java`:
  - `register` → `ResponseEntity<GenericResponse<UserResponse>>`
  - `login` → `ResponseEntity<GenericResponse<AuthResponse>>`
  - `refreshToken` → `ResponseEntity<GenericResponse<AuthResponse>>`
  - Verify các endpoint còn lại đã wrap đúng

**Files thêm:**
- `controller/AuthController.java` thêm endpoints:
  - `GET /api/v1/auth/me` (auth required) → trả về `GenericResponse<UserResponse>` từ principal
  - `POST /api/v1/auth/introspect` (auth required, hoặc service-to-service) → `{active, sub, roles, exp}` cho legacy/internal services

**Tests:**
- Update tất cả existing controller/service tests cho response shape mới
- Thêm `AuthControllerTest::me_authenticated_returnsProfile`
- Thêm `AuthControllerTest::me_unauthenticated_returns401`

**Acceptance:**
- Tất cả 10 endpoints (8 cũ + 2 mới) trả về `{success, message, result, statusCode}`
- OpenAPI spec regen → schemas đồng bộ
- Frontend integration test pass với shape mới

**Effort:** M (2 days) | **Dependencies:** Phase 1 (jti claim cho introspect)

**Risk:** Breaking change cho frontend → coordinate release. Có thể release behind header `X-Api-Version=v2` rồi dọn sau.

---

### Phase 6 — Password policy, audit logging, query optimization

**Mục tiêu:** Compliance + forensics readiness + performance.

**Files thêm:**
- `util/validation/StrongPassword.java` — annotation
- `util/validation/StrongPasswordValidator.java` — implement `ConstraintValidator<StrongPassword, String>`:
  - min 10 chars, ≥1 upper, ≥1 lower, ≥1 digit, ≥1 special (`!@#$%^&*()`)
  - Có thể dùng regex hoặc Passay library
- `service/AuditLogger.java`:
  - Method: `loginSuccess(userId, email, ip, userAgent, traceId)`, `loginFailure(...)`, `passwordChanged(...)`, `passwordReset(...)`, `accountActivated(...)`, `tokenRevoked(...)`
  - Implementation: log structured JSON với SLF4J + MDC

**Files sửa:**
- `model/dto/UserCreationRequest.java`, `ResetPasswordRequest.java`, `ChangePasswordRequest.java` — thay `@Size(min=8)` bằng `@StrongPassword`
- `model/entity/User.java` line 58 — `@ManyToMany(fetch = FetchType.LAZY)` thay EAGER
- `repository/UserRepository.java`:
  ```java
  @EntityGraph(attributePaths = "roles")
  Optional<User> findByEmailOrUsername(String email, String username);
  ```
  Áp dụng cho mọi method được dùng trong UserDetailsService và login flow
- `service/impl/AuthServiceImpl.java` — gọi `auditLogger.loginSuccess/Failure(...)` ở mỗi flow
- Kafka consumer trong các service khác (user-service) — kiểm tra không lazy-load `roles` ngoài transaction

**Tests:**
- `StrongPasswordValidatorTest` — test 8 weak cases và 5 valid cases
- `UserRepositoryTest` (`@DataJpaTest`) — verify `findByEmailOrUsername` chỉ ra 1 query (Hibernate stats)
- `AuditLoggerTest` — verify log structure

**Acceptance:**
- Password `password1` → 400 với message "must contain ≥1 uppercase, ≥1 special character"
- Login query produces ≤2 SQL statements (verify qua hibernate `generate_statistics=true`)
- Log file/sink chứa events `auth.login.success`, `auth.login.failure` với correlation ID

**Effort:** M (2 days) | **Dependencies:** Phase 0

---

### Phase 7 — Test coverage to ≥80%

**Mục tiêu:** Tự tin về behavior trước khi deploy.

**Files thêm:**
- `src/test/java/.../service/impl/AuthServiceImplTest.java` — extend với:
  - `register_*`: success, duplicateEmail, duplicateUsername, roleNotFound
  - `login_*`: success, badCredentials, unverifiedEmail, lockedAccount, bruteForceLockout, counterResetOnSuccess
  - `refreshToken_*`: success, expired, revoked, notFound
  - `logout_*`: success, tokenNotOwnedByUser, alreadyRevoked
  - `forgotPassword_*`: success, emailNotFound
  - `activateAccount_*`: success, userNotFound, kafkaFailFallbackToOutbox
  - `resendRegistrationOtp_*`: success, alreadyVerified, emailNotFound
- `src/test/java/.../service/impl/OtpServiceImplTest.java` — generate, verify (success/fail/expired), rate limit, hash storage, one-time-use deletion
- `src/test/java/.../service/impl/RateLimiterServiceImplTest.java` (Testcontainers Redis)
- `src/test/java/.../service/impl/EventPublisherImplTest.java` (Phase 4)
- `src/test/java/.../service/impl/OutboxRelayJobTest.java` (Phase 4)
- `src/test/java/.../controller/AuthControllerIT.java` — `@SpringBootTest` với Testcontainers (PostgreSQL + Redis + Kafka):
  - Happy path: register → consume OTP từ Kafka → verify → login → refresh → me → logout
  - Error matrix: bad password, expired OTP, brute force, etc.

**Files sửa:**
- `auth-service/pom.xml`:
  - Add `testcontainers-bom`, `org.testcontainers:postgresql`, `redis`, `kafka`, `junit-jupiter`
  - JaCoCo plugin với threshold:
    ```xml
    <rule>
      <element>BUNDLE</element>
      <limits>
        <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
        <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
      </limits>
    </rule>
    ```
- Maven profile `-DskipITs` cho dev local; CI luôn chạy IT

**Acceptance:**
- `mvn -pl auth-service verify` → JaCoCo gates pass
- ≥80% line, ≥70% branch coverage trên `service/impl` package
- CI pipeline chạy Testcontainers IT thành công

**Effort:** L (4 days) | **Dependencies:** Phases 0–6 (test cuối cùng theo API shape final)

---

### Phase 8 — Observability & ops readiness

**Mục tiêu:** Health probes, metrics, tracing để vận hành prod.

**Files sửa:**
- `auth-service/pom.xml`:
  - `spring-boot-starter-actuator`
  - `micrometer-registry-prometheus`
  - `micrometer-tracing-bridge-otel`
  - `opentelemetry-exporter-otlp`
- `application.yml` (hoặc config server):
  ```yaml
  management:
    endpoints.web.exposure.include: health,info,prometheus,metrics
    endpoint.health:
      show-details: when-authorized
      probes.enabled: true
    health:
      redis.enabled: true
      kafka.enabled: true
      db.enabled: true
    tracing.sampling.probability: 1.0
    metrics.tags.application: auth-service
  ```

**Files thêm:**
- `config/health/DbSeedHealthIndicator.java` — fail readiness nếu role `USER` không tồn tại trong DB (DB seed missing)
- `config/observability/AuthMetrics.java` (`@Component`) — register custom counters/timers:
  - `auth_login_total{result}`
  - `auth_otp_sent_total`, `auth_otp_verify_total{result}`
  - `auth_register_total`
  - `auth_ratelimit_blocked_total{flow}`
  - `auth_kafka_publish_total{topic,result}`
- `service/impl/AuthServiceImpl.java`, `OtpServiceImpl.java`, `RateLimiterServiceImpl.java`, `EventPublisherImpl.java` — increment counters

**Files sửa thêm:**
- `pom.xml` plugin `git-commit-id-maven-plugin` để `/actuator/info` expose commit + build time

**Acceptance:**
- `GET /actuator/health/liveness` → 200 OK
- `GET /actuator/health/readiness` → 200 OK khi DB seeded, 503 nếu role USER missing
- `GET /actuator/prometheus` → exposes custom metrics
- Trace ID propagate gateway → auth-service → Kafka (verify trong Tempo/Jaeger)

**Effort:** M (2 days) | **Dependencies:** None — chạy parallel với phase khác được

---

### Phase 9 — JWT blacklist (jti) for logout-immediate

**Mục tiêu:** Cho phép revoke access token ngay (không chờ exp).

**Files thêm:**
- `service/TokenBlacklist.java` (interface):
  ```java
  void revokeAccess(String jti, Instant exp);
  boolean isRevoked(String jti);
  ```
- `service/impl/TokenBlacklistImpl.java`:
  - Redis key `jwt:blacklist:{jti}` value `"1"`, TTL = `exp - now`

**Files sửa:**
- `controller/AuthController.java::logout`:
  - Hiện tại chỉ revoke refresh token; thêm:
    ```java
    String currentJti = jwtService.extractJti(<currentAccessTokenFromHeader>);
    Instant exp = jwtService.extractExpiration(currentJti);
    tokenBlacklist.revokeAccess(currentJti, exp);
    ```
- `service/JwtService.java` — thêm `Instant extractExpiration(String token)`
- `api-gateway/.../filters/AuthenticationFilter.java`:
  - Sau khi verify signature, check `tokenBlacklist.isRevoked(jti)` → reject 401 nếu blacklisted
  - Inject `StringRedisTemplate` (gateway đã có Redis dependency)
  - Cache local 10s để giảm Redis round-trip (Caffeine cache trong gateway)

**Tests:**
- `TokenBlacklistImplTest` (Testcontainers Redis)
- Integration test: login → call /me (200) → logout → call /me với token cũ (401)

**Acceptance:**
- Logout → access token cũ bị reject ngay lập tức
- Performance: gateway latency tăng <5ms (nhờ local cache)

**Effort:** M (2 days) | **Dependencies:** Phase 1 (jti)

**Risk:** Redis become single point of failure cho gateway → fallback "fail-open" (allow nếu Redis unreachable, log warning).

---

## Sequencing & Critical Path

```
Phase 0 (cleanup) ─→ Phase 1 (JWT) ─→ Phase 2 (verify+gateway) ─┐
                                  ↓                              │
                                  Phase 3 (brute-force)          ├─→ Phase 7 (tests) ─→ DEPLOY
                                  Phase 4 (kafka outbox)         │
                                  Phase 5 (response wrap) ───────┤
                                  Phase 6 (policy+audit) ────────┤
                                  Phase 9 (jwt blacklist) ───────┘
                                  Phase 8 (observability) [parallel]
```

**Tổng effort:** ~22 dev-days (S=1, M=2, L=4) ≈ 4–5 tuần với buffer.

---

## Critical Files (modified or created)

- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\service\impl\AuthServiceImpl.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\service\impl\JwtServiceImpl.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\service\impl\OtpServiceImpl.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\service\impl\TokenServiceImpl.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\controller\AuthController.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\config\security\AuthServiceKeyConfig.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\config\security\SecurityConfig.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\config\messages\KafkaConfig.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\model\entity\User.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\model\dto\VerifyOtpRequest.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\src\main\java\vn\iotstar\authservice\repository\UserRepository.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\api-gateway\src\main\java\vn\iotstar\apigateway\filters\AuthenticationFilter.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\api-gateway\src\main\java\vn\iotstar\apigateway\jwt\impl\JwtUtilImpl.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\utils\src\main\java\vn\iotstar\utils\exceptions\GlobalExceptionHandler.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\utils\src\main\java\vn\iotstar\utils\constants\GenericResponse.java
- D:\NovaPlay\NovaPlay_BE\NovaPlay\.claude\worktrees\quirky-poitras-672fa2\auth-service\pom.xml

**Existing utilities to reuse:**
- `vn.iotstar.utils.exceptions.wrapper.{BadRequestException, ResourceNotFoundException, ForbiddenException, UserAlreadyExistsException}`
- `vn.iotstar.utils.exceptions.GlobalExceptionHandler` (đã handle các exception trên)
- `vn.iotstar.authservice.service.impl.OtpServiceImpl` rate-limit pattern (line 35-39) — mẫu cho `RateLimiterServiceImpl`
- `vn.iotstar.authservice.service.impl.DedupStore` — mẫu cho `TokenBlacklistImpl`
- `org.springframework.data.redis.core.StringRedisTemplate` — đã inject sẵn ở các service Redis-based

---

## Verification Plan

### Per-phase smoke test

| Phase | Verification command |
|---|---|
| 0 | `mvn -pl auth-service clean compile -DskipTests` + `grep "RuntimeException(\|Provider" auth-service/src/main` → 0 hits |
| 1 | Decode JWT từ login response, verify có `iss`, `aud`, `jti`, `kid` header |
| 2 | `curl -X POST .../register` → `curl -X POST .../login` → expect 403 với message "Account not verified" |
| 3 | Loop 6 lần `curl .../login` với password sai → expect 6th lần trả 429 |
| 4 | `docker stop kafka` → `curl .../forgot-password` (200 OK) → query `outbox_events` table → 1 PENDING row |
| 5 | `curl .../login` → response có shape `{success, message, result, statusCode}` |
| 6 | `curl .../register` với password "password1" → 400 + policy violations |
| 7 | `mvn -pl auth-service verify` → JaCoCo report ≥80% line |
| 8 | `curl localhost:port/actuator/health/readiness` → 200; `curl .../actuator/prometheus | grep auth_login_total` → exists |
| 9 | login → `/me` (200) → logout → `/me` với token cũ → 401 trong vòng 1 giây |

### End-to-end integration test (sau Phase 7)

Chạy `AuthControllerIT` với Testcontainers — kịch bản:
1. Register user mới → expect 201 + user record + Kafka event published
2. Email-service consume event (mocked) → emit OTP
3. Verify OTP → expect 200 + `isEmailVerified=true`
4. Login → expect 200 + access/refresh token với đúng claims
5. GET `/me` → expect 200 với user profile
6. Refresh token → expect 200 + new access token + refresh token rotated
7. Change password → login với password cũ → expect 400; login mới → 200
8. Logout → access token cũ bị blacklist → `/me` → 401
9. Brute force: 6 login fail → 429
10. Forgot password flow: request OTP → reset password → login với password mới → 200

### Production deployment checklist

- [ ] DB migration script (drop providers, add outbox_events) tested on staging
- [ ] RSA private key loaded từ K8s Secret (không từ classpath)
- [ ] Config server có entries `auth.jwt.{issuer,audience,kid,expiration}`
- [ ] Kafka cluster có `min.insync.replicas≥2` cho topics `SEND_EMAIL`, `ACTIVATE_ACCOUNT`
- [ ] Redis cluster có persistence (AOF) và replica
- [ ] Prometheus scrape config trỏ đến `/actuator/prometheus`
- [ ] Grafana dashboard import (auth login rate, OTP success rate, p95 latency)
- [ ] Alert rules: login_failure_rate > 5%, ratelimit_blocked > 100/min, kafka_publish_fail > 0
- [ ] Runbook cho incidents: DB down, Redis down, Kafka down, key rotation
- [ ] Backup/restore test cho Token + OutboxEvent table
- [ ] Load test: 100 RPS login, 50 RPS register — verify p95 < 500ms

---

## Cross-Cutting Decisions (resolved)

| Decision | Choice |
|---|---|
| Dead interfaces (UserService, RoleService, KafkaService) | **Delete** |
| AdminController (rỗng) | **Delete** — recreate when admin API designed |
| OAuth2 (Provider entity, repo, mapper, DTO, processOAuth2Login) | **Delete entirely** — re-implement from scratch when needed |
| Response wrapping | **Wrap all responses** (breaking change accepted) |
| Brute-force pattern | **Redis counter** (reuse OtpService pattern, no Bucket4j dep) |
| JWT blacklist | **Implement** (Phase 9) |
| Strong password | **Implement** (Phase 6 — 10 char + complexity) |
| Kafka reliability | **Transactional outbox + retry + DLT** (Phase 4) |

# Auth Service — Thiết Kế & Quyết Định Kỹ Thuật

Tài liệu này ghi lại mục đích của `auth-service`, cách nó hoạt động, và các quyết định thiết kế quan trọng.

---

## Tổng quan

**Auth Service** là dịch vụ xác thực trung tâm của NovaPlay. Mọi yêu cầu đăng nhập, đăng ký, làm mới token đều đi qua đây. Service này sở hữu danh tính người dùng và cấp token JWT được API Gateway xác minh.

```
Client → API Gateway :8072
         (check JWT signature)
              ↓
      API Gateway injects X-User-Email header
              ↓
         Downstream Services :8000/8700/...
         (trust X-User-Email, no JWT verify)
```

**Stack:** Spring Boot 3.5, Spring Security, PostgreSQL, Redis, Kafka, RSA-256 JWT, Transactional Outbox Pattern.

**Cổng:** 8000

---

## 1. Quản lý Người Dùng

### Data Model

**User Entity** (PostgreSQL):
```
- id: UUID (primary key)
- username: VARCHAR(unique)
- email: VARCHAR(unique)
- password: VARCHAR (bcrypt-encoded)
- isActive: BOOLEAN (default true)
- isEmailVerified: BOOLEAN (default false, set true khi OTP verified)
- lastLoginAt: TIMESTAMP
- roles: ManyToMany → Role entities
- createdAt, updatedAt: TIMESTAMP (auto-managed)
```

**Role Entity:**
```
- id: UUID
- roleName: ENUM (USER, ADMIN, ...)
- description: VARCHAR
- permissions: ManyToMany → Permission entities
```

User mới đăng ký được gán **ROLE_USER** tự động ở `AuthServiceImpl.register()`.

### Đăng ký (Register)

```
POST /api/v1/auth/register
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123",
  "locale": "vi"
}
```

**Flow:**
1. Kiểm tra email/username đã tồn tại → 409 Conflict nếu có
2. Mã hóa password bằng **BCrypt** (`passwordEncoder.encode()`)
3. Lưu User vào PostgreSQL với `isEmailVerified = false`
4. Gán `ROLE_USER`
5. **Gọi OTP Service** để sinh OTP và gửi email (async qua Kafka)
6. Trả về 201 Created + UserResponse

**OTP Flow:**
- OTP 6 chữ số sinh ngẫu nhiên (`SecureRandom`)
- Hash OTP bằng BCrypt, lưu trong Redis với key `otp:<email>`, TTL = 5 phút
- Publish event `SEND_EMAIL` → Kafka → email-service gửi email OTP

### Xác thực OTP (Verify OTP)

```
POST /api/v1/auth/verify-otp
{
  "email": "john@example.com",
  "otp": "123456"
}
```

**Flow:**
1. Rate limit check: max 10 verify attempts per 15 minutes
2. Lấy hash OTP từ Redis key `otp:<email>`
3. So sánh OTP nhập vào vs hash bằng `passwordEncoder.matches()` (BCrypt compare)
4. Nếu OK → Xóa OTP khỏi Redis, **gọi `activateAccount(email)`**
5. **`activateAccount()`:**
   - Set `isEmailVerified = true` trên User
   - Publish event `ACTIVATE_ACCOUNT` → Kafka → user-service tạo profile
6. Trả về 200 OK

**Lý do BCrypt cho OTP:** Nếu Redis bị compromise, attacker không thể lấy OTP ngay lập tức (phải brute-force hash).

### Gửi lại OTP (Resend Registration OTP)

```
POST /api/v1/auth/resend-registration-otp
{
  "email": "john@example.com",
  "locale": "vi"
}
```

**Flow:**
1. Kiểm tra email tồn tại, `isEmailVerified = false`
2. Nếu account đã verified → 400 "Account is already verified"
3. Sinh OTP mới, gửi lại

**Rate limit:** Max 5 lần gửi/giờ

---

## 2. Đăng Nhập (Login)

```
POST /api/v1/auth/login
{
  "emailOrUsername": "john@example.com",
  "password": "SecurePass123"
}
```

**Flow:**
1. **Rate limit check:** Max 5 login failures per 15 minutes
   - Key: `auth:login:fail:<emailOrUsername>`
   - Nếu vượt limit → 429 Too Many Requests
   - Nếu thành công → Reset counter

2. **Spring Security Authentication:**
   ```java
   authenticationManager.authenticate(
     new UsernamePasswordAuthenticationToken(emailOrUsername, password)
   )
   ```
   - UserDetailsService load User từ DB
   - `PasswordEncoder.matches()` so sánh password (BCrypt)
   - Nếu sai → `BadCredentialsException` → Audit log "login_failure"
   - Nếu account chưa verify → `DisabledException` (isEnabled = false nếu isEmailVerified = false)
   - Nếu account bị lock → `LockedException`

3. **Cấp Token:**
   - **Access Token (JWT, 24h):** sinh bằng `jwtService.generateToken(user)`
   - **Refresh Token (7 ngày):** lưu trong DB PostgreSQL
   - Revoke tất cả refresh token cũ của user (1 user only 1 valid refresh token tại 1 thời điểm)

4. **Audit Log:**
   - Ghi lại login success: user id, email, timestamp
   - Ghi lại login failure: reason (bad_credentials, account_not_verified, account_locked)

5. **Trả về:**
   ```json
   {
     "accessToken": "eyJhbGc...",
     "refreshToken": "550e8400-e29b...",
     "expiresIn": 86400000,
     "userProfile": { "id": "...", "email": "...", "username": "..." }
   }
   ```

---

## 3. JWT Token (Access Token)

### JWT Claims

```json
{
  "sub": "john@example.com",       // user email
  "roles": ["ROLE_USER", ...],     // Roles từ database
  "iss": "novaplay-auth",          // Issuer (xác minh bởi gateway)
  "aud": "novaplay",               // Audience (xác minh bởi gateway)
  "jti": "uuid-string",            // JWT ID (dùng cho blacklist khi logout)
  "iat": 1700000000,               // Issued at
  "exp": 1700086400                // Expiration (24h)
}
```

### JWT Signing

```
Header: { "alg": "RS256", "kid": "v1" }
Signature: HMAC-SHA256 bằng RSA private key
```

**Lý do RSA-256 (asymmetric):**
- Auth-service ký bằng **private key** (`../docker-compose/secrets/private.pem`)
- API Gateway xác minh bằng **public key** (không cần private key)
- Nếu downstream service cần xác minh JWT → có public key, không cần auth-service

### JwtServiceImpl

**`generateToken(user)`:**
- Lấy roles từ User → map thành `["ROLE_USER", ...]`
- Thêm claims: `iss`, `aud`, `jti` (UUID random), `iat`, `exp`
- Ký bằng RSA private key
- Compact → Base64 JWT string

**`extractEmail/Roles/Jti/Expiration(token)`:**
- Parse JWT bằng public key
- Extract các claims cần thiết

**`isTokenValid(token)`:**
- Parse JWT (auto verify signature + expiration)
- Trả về true nếu không expired

---

## 4. Refresh Token

### Data Model

**Token Entity** (PostgreSQL):
```
- id: UUID (primary key)
- tokenValue: VARCHAR(700, unique)  ← UUID random
- type: ENUM (REFRESH_TOKEN)
- isRevoked: BOOLEAN (default false)
- expiredAt: INSTANT (7 ngày)
- user: ManyToOne → User
- createdAt, updatedAt
```

### Refresh Flow

```
POST /api/v1/auth/refresh-token
{
  "refreshToken": "550e8400-e29b..."
}
```

**Flow:**
1. Tìm Token trong DB by `tokenValue`
2. Kiểm tra:
   - Không revoked (`isRevoked = false`)
   - Chưa expired (`expiredAt > now`)
3. Nếu OK → Sinh **access token mới** bằng `jwtService.generateToken(user)`
4. Refresh token **không thay đổi** (cùng token trả lại)
5. Trả về access token mới + refresh token cũ

**Đặc điểm:**
- **1 user = 1 refresh token hợp lệ** tại 1 thời điểm
  - Khi login lần 2 → revoke refresh token lần 1
  - `revokeAllUserTokens(user)` trước khi sinh token mới
- **Expiration 7 ngày:** Cài đặt trong `application-dev.yml`: `spring.application.security.jwt.refresh-token.expiration: 604800000` (ms)

---

## 5. Logout (Token Revocation)

### Logout Access Token (jti Blacklist)

Khi logout, access token cũng bị revoke qua API Gateway:

```
POST /api/v1/auth/logout
{
  "refreshToken": "550e8400-e29b..."
}
Authorization: Bearer <accessToken>
```

**Flow:**
1. Extract `jti` từ access token header
2. Gọi `TokenBlacklist.revoke(jti, expiration_date)`
   - Lưu vào Redis: `jwt:blacklist:<jti>` với TTL = (token expiration time - now)
3. Revoke refresh token trong DB
4. Trả về 204 No Content

**`TokenBlacklistImpl`:**
```java
public void revoke(String jti, Date exp) {
    Duration ttl = Duration.between(Instant.now(), exp.toInstant());
    redis.opsForValue().set("jwt:blacklist:" + jti, "revoked", ttl);
}

public boolean isRevoked(String jti) {
    return redis.hasKey("jwt:blacklist:" + jti);
}
```

**API Gateway check:**
- Mỗi request, AuthenticationFilter check `redisTemplate.hasKey("jwt:blacklist:" + jti)`
- Nếu true → 401 "Token has been revoked"

---

## 6. Password Management

### Change Password (xác thực cũ)

```
POST /api/v1/auth/change-password
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456",
  "confirmNewPassword": "NewPass456"
}
Authorization: Bearer <accessToken>
```

**Flow:**
1. Lấy user từ `@AuthenticationPrincipal User user`
2. Verify current password: `passwordEncoder.matches(currentPassword, user.getPassword())`
3. Kiểm tra:
   - New password ≠ current password
   - New password = confirm password
4. Mã hóa password mới, lưu DB
5. Audit log "password_changed"
6. Trả về 200 OK

### Forgot Password → Reset Password (OTP-based)

**1. Forgot Password:**
```
POST /api/v1/auth/forgot-password
{
  "email": "john@example.com",
  "locale": "vi"
}
```
- Sinh OTP, gửi email

**2. Reset Password:**
```
POST /api/v1/auth/reset-password
{
  "email": "john@example.com",
  "otp": "123456",
  "newPassword": "NewPass456",
  "confirmNewPassword": "NewPass456"
}
```

**Flow:**
1. Verify OTP (như section xác thực OTP)
2. Kiểm tra:
   - New password ≠ old password (dùng `passwordEncoder.matches()`)
   - New password = confirm password
3. Mã hóa password mới, lưu DB
4. Audit log "password_reset"

**Lý do kiểm tra password cũ vs mới:** Tránh user reset thành password giống cũ (vô nghĩa).

---

## 7. Rate Limiting

`RateLimiterServiceImpl` dùng Redis để track và giới hạn:

**Login failures:**
- Key: `auth:login:fail:<emailOrUsername>`
- Max 5 failures per 15 minutes

**OTP verification:**
- Key: `auth:otp:verify:<email>`
- Max 10 attempts per 15 minutes

**OTP send:**
- Key: `otp:send:cnt:<email>`
- Max 5 sends per hour

**Implementation:**
```java
public void checkAndIncrement(String key, int limit, Duration window) {
    Long count = redis.opsForValue().increment(key);
    if (count == 1) {
        redis.expire(key, window);  // Set TTL on first increment
    }
    if (count > limit) {
        throw new TooManyRequestsException(...);
    }
}

public void reset(String key) {
    redis.delete(key);
}
```

---

## 8. Async Event Publishing (Transactional Outbox)

### Vấn đề: Kafka vs Database Consistency

Nếu publish trực tiếp:
```
1. Lưu User vào DB
2. Publish Kafka event
3. ← Kafka down, event lost nhưng user đã tạo
```

### Giải pháp: Transactional Outbox Pattern

```
1. Lưu User vào DB
2. Lưu event vào outbox_events table (cùng transaction)
3. ← DB commit OK
4. OutboxRelayJob (background) poll outbox_events (PENDING)
5. Publish Kafka
6. Update status SENT
```

**OutboxEvent Entity:**
```
- id: UUID
- topic: VARCHAR (e.g., "user-register")
- key: VARCHAR (e.g., user id)
- payload: JSONB
- status: ENUM (PENDING, SENT, FAILED)
- attempts: INT (0-5)
- createdAt, sentAt, errorMessage
```

**EventPublisherImpl:**
```
1. Serialize payload → JSON
2. Lưu OutboxEvent (status=PENDING) vào DB (trong transaction)
3. Try publish Kafka ngay
4. Nếu fail, OutboxRelayJob sẽ retry
```

**OutboxRelayJob:**
```
@Scheduled(fixedDelay = 5000)  ← Chạy mỗi 5 giây
public void relay() {
    1. Query `outbox_events where status=PENDING` (max 100)
    2. For each event:
       - Nếu attempts >= 5 → status=FAILED
       - Else:
         - Try kafkaTemplate.send()
         - Nếu OK → status=SENT
         - Nếu fail → attempts++
}
```

### Events

| Topic | Sent By | Consumed By | Payload |
|-------|---------|-------------|---------|
| `SEND_EMAIL` | auth-service (register/forgot-password) | email-service | `EmailOtpRequested` (userId, email, otp, locale) |
| `ACTIVATE_ACCOUNT` | auth-service (verify-otp) | user-service | `UserRegister` (username, email) |

---

## 9. Audit Logging

`AuditLogger` ghi lại mọi action quan trọng:

```
- login success: user id, email, timestamp
- login failure: reason (bad_credentials, account_locked, account_not_verified)
- password reset: email
- password changed: user id, email
- account activated: user id, email
```

**Mục đích:** Trace người dùng, phát hiện dị thường (brute force, password reset lạ, etc.)

---

## 10. Service-to-Service Token Introspection

Nếu user-service cần verify token mà không có public key:

```
POST /api/v1/auth/introspect
Authorization: Bearer <token>
```

**Response:**
```json
{
  "active": true,
  "sub": "john@example.com",
  "roles": ["ROLE_USER"],
  "jti": "uuid-jti"
}
```

**Use case:** user-service gọi auth-service để double-check token trước khi update profile.

---

## 11. Architecture & Technology Choices

### Spring Security Configuration

`SecurityConfig` cấu hình:
- Authentication provider: DaoAuthenticationProvider + BCrypt PasswordEncoder
- User details service: Load từ `UserRepository`
- JWT config: RSA public key, issuer, audience

**Tại sao Spring Security + Custom JWT?**
- Spring Security giải quyết bằng cấp access control, password encoding, user loading
- Custom JwtService xử lý JWT ký/xác minh (không dùng Spring Security OAuth2 vì cần custom claims)
- Đơn giản hơn OAuth2 flow, phù hợp với internal service

### Transactional Outbox vs Direct Kafka

| | Direct Kafka | Transactional Outbox |
|---|---|---|
| Atomicity | Không (fail Kafka sau DB save) | Có (cùng transaction) |
| Complexity | Thấp | Cao (extra table + relay job) |
| Reliability | Có thể mất event | Guaranteed delivery (retry) |
| Latency | Thấp (ngay lập tức) | Cao (5s relay delay) |

**Chọn Outbox vì:** Email notification (OTP) và account activation là critical events → không thể mất.

### PostgreSQL vs MongoDB

| | PostgreSQL (user/token) | MongoDB (khác) |
|---|---|---|
| Schema | Structured (user, role, permission) | Flexible (event document) |
| ACID | Có (transactions) | Có (4.0+) |
| Queries | Complex joins | Document queries |
| Consistency | Strong | Eventual |

**Auth-service dùng PostgreSQL** vì schema cố định, cần ACID transactions.

### Redis vs In-Memory

| | Redis | HashMap |
|---|---|---|
| Distributed | Có | Không |
| Persistence | Optional | Không |
| Expiration | Có (TTL) | Phải implement |
| Size limit | Virtual memory | Heap limited |

**Dùng Redis** vì:
- OTP, rate limit key có TTL tự động
- Shared state across API Gateway instances (nếu scale horizontal)
- Logout/blacklist persisted (k restart mất)

---

## 12. Known Issues & Future Improvements

### Issues Hiện Tại

1. **P2: Kafka Duplicate Sends**
   - Nếu OutboxRelayJob gửi lần 1 OK, nhưng DB save fail → retry gửi lần 2 (duplicate event)
   - Fix: Kafka idempotence producer config (`enable-idempotence: true`) + check `sendAt` before update

2. **P3: activateAccount Idempotency**
   - Nếu verify-otp retry → activateAccount call multiple times → `UserRegister` event duplicate
   - Fix: Check if already verified before publishing event

3. **P3: OTP Edge Case**
   - Nếu user gửi OTP nhưng không verify, verify window lapse → OTP key expired → cannot verify
   - Fix: Extend TTL sau mỗi verify attempt

4. **P3: Refresh Token Masking**
   - Trả refresh token cho client ở response login/refresh
   - Nếu attacker intercept → có thể dùng refresh token
   - Fix: Rotate refresh token trên mỗi lần refresh (current token → new token)

### Future Improvements

1. **Multi-factor Authentication (MFA)** - OTP via SMS, TOTP apps
2. **Social Login** - OAuth2 provider (Google, GitHub)
3. **Token Rotation** - Refresh token tự động rotate
4. **Device Tracking** - Track login devices, revoke từ device
5. **IP Whitelisting** - Restrict login từ known IPs
6. **Backup Codes** - Account recovery nếu mất 2FA

---

## 13. Deployment Checklist

- ✅ RSA keys (`private.pem`, `public.pem`) generated và deploy
- ✅ PostgreSQL migration (user, role, token, permission tables)
- ✅ Redis instance available (OTP, rate limit, blacklist)
- ✅ Kafka bootstrap servers configured
- ✅ Email service running (consume `SEND_EMAIL` topic)
- ✅ User service running (consume `ACTIVATE_ACCOUNT` topic)
- ✅ API Gateway có public.pem để verify JWT
- ✅ Monitoring/alerting trên OutboxEvent status=FAILED

---

## 14. References

- **JWT:** https://tools.ietf.org/html/rfc7519
- **Transactional Outbox:** https://microservices.io/patterns/data/transactional-outbox.html
- **Spring Security:** https://spring.io/projects/spring-security
- **Kafka Idempotence:** https://kafka.apache.org/documentation/#idempotence

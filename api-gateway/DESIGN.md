# API Gateway — Thiết Kế & Quyết Định Kỹ Thuật

Tài liệu này ghi lại những thay đổi đã thực hiện trên `api-gateway`, lý do đưa ra từng quyết định, và so sánh các phương án.

---

## Tổng quan

API Gateway là điểm vào duy nhất của hệ thống NovaPlay. Mọi request từ client (React/Vite) đều đi qua đây trước khi đến các downstream service.

```
Client → API Gateway :8072 → Eureka Discovery → Downstream Services
                                                 ├── auth-service :8000
                                                 ├── user-service :8700
                                                 └── movie-service
```

**Stack:** Spring Cloud Gateway (WebFlux/reactive), Java 21, Spring Boot 3.5, Spring Cloud 2025.0.0

---

## 1. Loại bỏ Spring Cloud Config

**Trước:** `application.yml` kéo cấu hình từ config-server khi khởi động.

**Sau:** Toàn bộ cấu hình nằm trong `application-dev.yml` — standalone, không phụ thuộc config-server.

| | Cloud Config | Standalone YAML |
|---|---|---|
| Khởi động | Phải có config-server trước | Độc lập, chạy bất kỳ lúc nào |
| Debug | Cần xem 2 nơi (config-server + service) | Mọi thứ trong 1 file |
| Phù hợp | Production với nhiều service, centralized config | Dev, staging, monorepo |
| Rủi ro | Config-server down → gateway không start | Không có |

**Lý do chọn standalone:** Dự án chạy monorepo, config-server tạo ra dependency không cần thiết ở môi trường dev.

---

## 2. YAML-only Routes (bỏ Java RouteLocator)

**Trước:** Routes được định nghĩa bằng Java `RouteLocator` bean với fluent API.

**Sau:** Toàn bộ 6 routes (auth, user, movie + 3 swagger) định nghĩa trong `application-dev.yml`.

| Tiêu chí | Java RouteLocator | YAML-only |
|---|---|---|
| Vị trí config | Java class | `application-dev.yml` |
| Thêm route mới | Sửa code, rebuild | Sửa YAML, restart |
| Đọc hiểu | Phải đọc code | Đọc trực tiếp |
| Phù hợp với GlobalFilter | Bắt buộc (GatewayFilter gắn vào từng route) | Hoạt động tự động |
| Hệ thống lớn thực tế | Ít dùng (khó scale) | Chuẩn công nghiệp |

```yaml
# Ví dụ cấu trúc route trong YAML
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service           # load-balanced qua Eureka
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: CircuitBreaker
              args:
                name: auth-service-cb
                fallbackUri: forward:/fallback/message
            - name: Retry
              args:
                retries: 3
                methods: GET
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@userEmailOrIpKeyResolver}"
```

**`RouteConfig.java` giờ chỉ chứa 3 bean hỗ trợ:**
- `RedisRateLimiter` — cấu hình token bucket
- `defaultCustomizer` — cấu hình Resilience4J factory
- `userEmailOrIpKeyResolver` — logic chọn key cho rate limiting

---

## 3. AuthenticationFilter: GatewayFilter → GlobalFilter

Đây là thay đổi quan trọng nhất, là điều kiện tiên quyết để YAML-only routing hoạt động được với authentication.

| | `GatewayFilter` | `GlobalFilter` |
|---|---|---|
| Áp dụng cho | Route cụ thể (phải khai báo trong mỗi route) | **Tất cả routes tự động** |
| Gắn vào YAML route | Phải có bean tên khớp, dễ sai | Không cần khai báo gì |
| Ordered | Qua `GatewayFilter.apply()` | Implement `Ordered` interface |
| Dùng với `@RefreshScope` | Cần thiết khi dùng cloud-config | **Bỏ được** (không còn cloud-config) |

```java
// Trước: GatewayFilter — chỉ chạy nếu route khai báo filter này
public class AuthenticationFilter implements GatewayFilterFactory<...> { ... }

// Sau: GlobalFilter — chạy cho mọi request, không cần route biết đến
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }
}
```

**Tại sao `HIGHEST_PRECEDENCE + 1` (tức `-2147483647`)?**

`AuthenticationFilter` cần inject `X-User-Email` vào header **trước** khi `RequestRateLimiter` chạy — vì rate limiter dùng header đó làm key. Đặt order âm lớn đảm bảo filter auth chạy trước tất cả route-level filters.

```
Order -2147483647  →  AuthenticationFilter  (inject X-User-Email)
Order -1           →  ResponseTimeHeaderFilter
Route filters      →  CircuitBreaker → Retry → RequestRateLimiter (đọc X-User-Email)
```

---

## 4. Bảo mật Header Injection (`sanitizeAndEnrich`)

**Vấn đề:** Client độc hại có thể gửi request kèm header `X-User-Email: admin@example.com` để giả mạo danh tính, vượt qua phân quyền ở downstream service.

**Giải pháp:** `sanitizeAndEnrich` — xóa header cũ từ client, chỉ inject lại từ JWT đã xác thực.

```java
private ServerWebExchange sanitizeAndEnrich(ServerWebExchange exchange, Claims claims) {
    ServerHttpRequest mutated = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove("X-User-Email");   // Xóa header từ client
                h.remove("X-User-Roles");
            })
            .header("X-User-Email", claims.getSubject())  // Inject từ JWT đã verify
            .header("X-User-Roles", String.valueOf(claims.get("roles")))
            .build();
    return exchange.mutate().request(mutated).build();
}
```

**Downstream services** (user-service, movie-service) tin tưởng hoàn toàn vào `X-User-Email` và `X-User-Roles` headers — không cần verify JWT, không cần giữ RSA public key.

```
Client ──(JWT)──→ Gateway verify JWT → inject X-User-Email → Downstream đọc header
                  ↑ RSA verify ở đây                         ↑ không cần JWT nữa
```

---

## 5. Sửa lỗi: `jti == null` → 401

**Trước:** Token không có `jti` claim vẫn được cho qua — không thể blacklist token khi logout.

**Sau:** Token thiếu `jti` trả về 401 ngay lập tức.

```java
String jti = claims.getId();
if (jti == null) {
    return this.onError(exchange, "Token missing jti claim");  // Chặn lại
}

// Kiểm tra Redis blacklist
return redisTemplate.hasKey("jwt:blacklist:" + jti)
        .flatMap(blacklisted -> {
            if (Boolean.TRUE.equals(blacklisted)) {
                return onError(exchange, "Token has been revoked");
            }
            return chain.filter(sanitizeAndEnrich(exchange, claims));
        });
```

**Logout flow:** auth-service lưu `jwt:blacklist:<jti>` vào Redis với TTL = thời gian còn lại của token. Gateway check Redis ở mỗi request.

---

## 6. Sửa lỗi: `AntPathMatcher` thay cho `contains()`

**Trước:** Kiểm tra public endpoint bằng `path.contains("/auth/login")` — fail với path không chuẩn, không hỗ trợ wildcard.

**Sau:** Dùng `AntPathMatcher.match(pattern, path)` — hỗ trợ `/**`, `/*`, pattern chuẩn Spring.

```java
// Trước — dễ false-positive, không hỗ trợ wildcard
boolean isPublic = PublicEndpoints.AUTH.stream().anyMatch(path::contains);

// Sau — pattern matching chính xác
private final AntPathMatcher pathMatcher = new AntPathMatcher();
boolean isPublic = PublicEndpoints.ALL.stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
```

**Public endpoints hiện tại:**

| Endpoint | Lý do public |
|---|---|
| `/api/v1/auth/register` | Đăng ký tài khoản |
| `/api/v1/auth/login` | Đăng nhập |
| `/api/v1/auth/verify-otp` | Xác thực OTP |
| `/api/v1/auth/resend-registration-otp` | Gửi lại OTP |
| `/api/v1/auth/forgot-password` | Quên mật khẩu |
| `/api/v1/auth/reset-password` | Đặt lại mật khẩu |
| `/api/v1/auth/refresh-token` | Làm mới access token |
| `/fallback/**` | Trang lỗi fallback |
| `/swagger/**` | Swagger UI aggregated |
| `/actuator/**` | Health check, metrics |
| `/v3/api-docs/**`, `/webjars/**` | Swagger resources |

---

## 7. Resilience: CircuitBreaker + Retry + RateLimiter

Toàn bộ cấu hình resilience trong YAML, không dùng Java `useCircuitBreaker()`.

### Circuit Breaker (Resilience4J)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10          # Cửa sổ 10 request gần nhất
        failureRateThreshold: 50       # Mở circuit nếu ≥50% thất bại
        permittedNumberOfCallsInHalfOpenState: 2  # Thử 2 call khi half-open
        waitDurationInOpenState: 10000 # Đợi 10s trước khi thử lại
```

**3 trạng thái Circuit Breaker:**

```
CLOSED ──(≥50% fail)──→ OPEN ──(10s)──→ HALF-OPEN ──(OK)──→ CLOSED
  ↑ request đi qua        ↓ fallback     ↓ thử 2 call         ↑
  ←──────────────────────────────────────(fail)────────────────
```

### Retry

```yaml
- name: Retry
  args:
    retries: 3          # Tối đa 3 lần retry
    methods: GET        # Chỉ retry GET (safe, idempotent)
    backoff:
      firstBackoff: 100ms
      maxBackoff: 1000ms
      factor: 2         # 100ms → 200ms → 400ms → (capped 1000ms)
      basedOnPreviousValue: true
```

**Tại sao chỉ retry GET?** POST/PUT/DELETE không idempotent — retry có thể tạo dữ liệu trùng.

### Rate Limiter (Token Bucket via Redis)

```yaml
redis-rate-limiter.replenishRate: 10   # 10 token/giây nạp vào
redis-rate-limiter.burstCapacity: 20   # Tối đa 20 token trong bucket
key-resolver: "#{@userEmailOrIpKeyResolver}"
```

**Key resolver logic:**
1. Nếu request đã xác thực → key = `X-User-Email` (rate limit per user)
2. Nếu chưa xác thực → key = IP address
3. Nếu không lấy được IP → key = `"unknown"`

**movie-service** không có RateLimiter (public content, chỉ có CircuitBreaker + Retry).

---

## 8. JWT Verification (RSA)

```
auth-service                    api-gateway
├── private.pem (ký JWT)        └── certs/public.pem (verify JWT)
│
└── Tạo JWT với:
    - sub: user email
    - roles: [ROLE_USER, ...]
    - jti: UUID (dùng cho blacklist)
    - iss: novaplay-auth
    - aud: novaplay
```

**JwtUtilImpl** verify:
1. Chữ ký RSA (tự động qua `Jwts.parserBuilder().setSigningKey(publicKey)`)
2. `iss` = `novaplay-auth`
3. `aud` = `novaplay`
4. Expiration (tự động qua jjwt)

Downstream services **không cần** RSA key — chỉ đọc header `X-User-Email` và `X-User-Roles`.

---

## 9. CORS & Security

**SecurityConfig** dùng Spring Security WebFlux nhưng `anyExchange().permitAll()` — tức là tắt Spring Security authentication. Lý do: authentication được xử lý hoàn toàn bởi `AuthenticationFilter` (custom GlobalFilter), không dùng Spring Security auth.

Spring Security chỉ đóng vai trò **CORS handler** — xử lý preflight OPTIONS request trước khi request vào gateway.

```java
.authorizeExchange(exchange -> exchange
    .anyExchange().permitAll()  // Spring Security không chặn gì
)
// Auth thực sự nằm ở AuthenticationFilter.java
```

**Allowed origins** (dev): `localhost:5173`, `localhost:3000`, `127.0.0.1:5173`

---

## 10. Observability

| Công cụ | Mục đích |
|---|---|
| Prometheus `/actuator/prometheus` | Thu thập metrics |
| OpenTelemetry Java Agent | Distributed tracing tự động |
| Loki (qua Alloy) | Log aggregation |
| Grafana :3000 | Dashboard tổng hợp |

Log pattern include `traceId` và `spanId` để correlate logs với traces:

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
```

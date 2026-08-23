# Copilot Instructions for NovaPlay

> **NovaPlay:** Production-grade microservices movie streaming platform. This guide helps AI agents understand architecture, build systems, patterns, and deployment.

## Critical Architecture: Two Build Systems

**This is NOT a single-build project.** Understanding this is essential to every task:

| Aspect | Maven Services (10) | Gradle Services (2) |
|--------|-------------------|-------------------|
| Services | movie, notification, user, payment, promotion, streaming, report, media, config, transcoding-worker | **api-gateway**, **auth-service** |
| Build File | Root `pom.xml` with `<modules>` | Standalone `build.gradle` per service |
| Wrapper | `./mvnw` at repo root | `./gradlew` **inside service dir** |
| Java | 21 | 25 (toolchain) |
| Spring Boot | 3.5.0 | 4.1.0 |
| Build Command | `./mvnw clean install -pl <svc>` | `cd <svc> && ./gradlew build` |
| Test Command | `./mvnw test -pl <svc>` | `cd <svc> && ./gradlew test` |
| Docker Context | Repo root (`.`) | Service dir (`<svc>`) |

⚠️ **Before running ANY build/test command, check which system the file belongs to.** The Gradle pair is **one version generation ahead** (Spring Boot 4.1.0 vs 3.5.0). A breaking change on one side must be validated against the other.

### Build Examples

```bash
# Maven side
./mvnw clean install -pl movie-service -DskipTests
./mvnw test -pl user-service

# Gradle side (must cd into service first)
cd api-gateway && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd auth-service && ./gradlew test --tests 'SomeTest.someMethod'

# Build parent POM only (for per-service CI)
./mvnw -B -q install -N
```

---

## No Shared Library — Intentional Duplication

**Critical pattern:** There is NO shared `utils` artifact. Every service owns its own copy of:
- `GenericResponse` (common response wrapper)
- `GlobalExceptionHandler` + exception types
- Auditing base classes (`BaseEntity`, audit annotations)
- Kafka event records (same event may exist in multiple services)
- Topic name constants (`util/TopicNames.java` or `util/TopicName.java`)
- Transactional outbox package (auth-service, promotion-service only)

All are under `vn.iotstar.<service>` namespace. **When a cross-service change touches duplicated code, update EVERY service that needs it individually.** Do not create a shared module.

**Why?** Independent versioning and deployment per service. Each service builds, versions, and deploys on its own without waiting for shared dependencies.

---

## Service Ports & Startup Order

| Service | Port | Build | Kafka Producer | Kafka Consumer |
|---------|------|-------|--------|--------|
| api-gateway | 8072 | Gradle | — | — |
| auth-service | 8000 | Gradle | send-email.v1, activate-account.v1 | — |
| user-service | 8700 | Maven | — | send-status-media.v1 |
| movie-service | 8600 | Maven | — | — |
| streaming-service | 8200 | Maven | — | — |
| media-service | 8081 | Maven | send-status-media.v1, video-source-ready.v1, video-transcode-completed.v1 | — |
| transcoding-worker | 8400 | Maven | — | video-source-ready.v1 |
| notification-service | 8900 | Maven | — | send-email.v1, activate-account.v1, notification.requested.v1 |
| promotion-service | 8300 | Maven | create-referral.v1, qualify-referral.v1, redeem-coupon.v1 | — |
| config-service | 8500 | Maven | — | — |
| payment-service | *none* | Maven | — | — |
| report-service | *none* | Maven | — | — |

**Startup order (local dev):**
1. Infrastructure: `docker compose -f docker-compose/qa/docker-compose.yml up -d`
2. `api-gateway` :8072 (gateway must be first)
3. All others in any order

---

## Request Flow & Authentication

```
React Frontend → API Gateway :8072
                 (AuthenticationFilter: JWT verification, header injection)
                 ↓
              X-User-Email, X-User-Roles headers injected
              (client-supplied headers STRIPPED & replaced)
                 ↓
              Downstream Services
              (trust headers without JWT re-verification)
```

### JWT Lifecycle

**Issued by:** `auth-service` (RSA-256 signing, private key at `src/main/resources/keys/private.pem`)

**Verified by:** `api-gateway` (RSA-256 verification, public key at `src/main/resources/certs/public.pem`)

**Token contents:** email, roles (from JWT claims) → injected into `X-User-Email` / `X-User-Roles` headers

**Revocation:** Redis blacklist at key `jwt:blacklist:<jti>` (TTL = token's remaining exp time); checked before every route

**Playback token** (streaming-service only): Separate short-lived HMAC JWT (4h TTL), proving "user may play this manifest". NOT the login JWT. Configured in `playback.token-secret`.

### Gateway-Only Enforcement

Every downstream service has `GatewayAuthFilter` (`config/security/`) that rejects requests WITHOUT valid `X-Gateway-Auth` shared secret. This proves the `X-User-Email` header came through the gateway, not spoofed. Toggled by `application.security.gateway-secret.{enabled,value}`:
- **dev:** disabled (services callable directly)
- **prod:** mandatory (filter fails startup if enabled without a secret)

---

## Inter-Service Communication: OpenFeign

Services call each other via **OpenFeign** with explicit base URLs (injected via `services.<name>` config property).

**Two request-scoped identity patterns — choosing the wrong one is a production bug:**

| Config Class | Identity Sent | Use For | Location |
|---|---|---|---|
| `FeignSecurityConfig` | Relay caller's `X-User-Email` / `X-User-Roles` | Owner-gated calls (user's own data) | `config/security/` |
| `ServiceIdentityFeignConfig` | Hardcoded `system@<service>` + `[ROLE_SERVICE]` | Machine-to-machine reads bypassing owner gating | `config/security/` |

**Implementation:** Both are intentionally NOT `@Configuration` annotated (prevents global scan). Attach per client:
```java
@FeignClient(name = "user-service", 
             url = "${services.user-service}", 
             configuration = FeignSecurityConfig.class)
public interface UserServiceClient { ... }
```

---

## Kafka Topics & Outbox Pattern

| Topic | Producer → Consumer | Pattern |
|---|---|---|
| send-email.v1 | auth-service → notification-service | Transactional Outbox |
| activate-account.v1 | auth-service → notification-service | Transactional Outbox |
| notification.requested.v1 | any → notification-service | Direct publish |
| send-status-media.v1 | media-service → user-service | Direct publish |
| video-source-ready.v1 | media-service → transcoding-worker | Direct publish |
| video-transcode-completed.v1 | media-service → (fan-out) | Direct publish |
| create-referral.v1, qualify-referral.v1, redeem-coupon.v1 | promotion-service | Transactional Outbox |

**Failed messages:** Go to `<topic>.DLT` (dead-letter topic); notification-service has dedicated `DeadLetterConsumer`.

**Transactional Outbox (auth-service, promotion-service):**
1. INSERT into `outbox_events` inside the business transaction (same connection)
2. After COMMIT, `TransactionSynchronization` hook hands row ID to in-process relay pool
3. Publisher & relay share the same JVM (no cross-process wake-up)
4. `OutboxSweepJob` re-claims orphans every 60s (`FOR UPDATE SKIP LOCKED`; pods share work)
5. Row deleted only after Kafka ack (at-least-once delivery)

**Type Mappings:** Kafka events are Kafka-local (no FQCN `__TypeId__` header). Use `JsonSerializer.TYPE_MAPPINGS` / `JsonDeserializer.TYPE_MAPPINGS` with logical names OR disable type headers and declare target type explicitly.

---

## Video Pipeline: Upload → Transcode → Playback

Three services form one chain via Kafka + object storage (no cross-database reads):

```
browser → presigned PUT → object storage (S3-compatible: MinIO/R2/B2)
                               ↓
                          media-service owns VideoManifest lifecycle
                               ↓ publishes video-source-ready.v1
                          transcoding-worker (ffmpeg → HLS ladder + AES-128)
                          writes segments/playlists to storage
                               ↓ Feign: manifest complete/fail
                          media-service updates manifest status
                               ↓ publishes video-transcode-completed.v1

streaming-service:
  - Resolves entitlement (Redis-cached)
  - Fetches manifest (Redis-cached)
  - Streams HLS segments from storage
```

**Storage Provider Strategy:** Abstracted via `StorageProvider` + `OpenFeature` flag `media-storage-provider`:
- Default env: `DEFAULT_STORAGE_PROVIDER` (aws-s3, cloudflare-r2, or backblaze-b2)
- Override in config-service Mongo: stored flag value takes precedence
- Only new uploads follow the flag; existing records keep their original provider
- **All three services must point to the same bucket** or playback breaks

**Playback Security (two separate mechanisms):**
1. **Playback token** (`PlaybackTokenService`) — short-lived HMAC JWT (4h TTL) proving "user may play this manifest". HLS players can't attach `Authorization` header, so playback token guards `/api/v1/streaming/hls/**` (permitAll).
2. **HLS key wrapping** — segments encrypted AES-128. Shared secret `TRANSCODE_KEY_WRAP_SECRET` (AES-GCM) between transcoding-worker's `KeyWrapService` and streaming-service's `KeyUnwrapService`. Unwrapped keys never cached or logged.

---

## Configuration & Profiles

Each service has:
- `application.yml` — only `spring.application.name`
- `application-dev.yml` — full standalone config (commits placeholder secrets on purpose)

Activate dev profile: `SPRING_PROFILES_ACTIVE=dev` or `-Dspring.profiles.active=dev`

**Dev placeholder secrets** (intentional, for dev reproducibility):
- `transcode.key-wrap-secret` = fixed base64 of 32 known bytes
- Other placeholders in `application-dev.yml`

**Prod overrides** via env vars in `application-prod.yml` — never promote dev defaults to prod.

---

## Deployment: Local Docker Only

⚠️ **There is NO deployed cluster. The only execution environment is `tilt up` on a developer's laptop (Docker Desktop + k3s).**

Consequences:
- `k8s/overlays/prod/` (ingress, DNS, Let's Encrypt) describe a **theoretical cluster that has never run**. Keep it internally coherent; never cite as "how it's deployed."
- **Only real deploy path:** `tilt up` against local k3s cluster, entry point `http://localhost`
- **CI:** Builds & tests only. **No CD.** Nothing pushes images to any cluster.
- **HPA, PDB, resource requests:** Sized for 1–2 VPS (~8 vCPU); on one laptop they over-commit.

### Managed Cloud Services (Free Tiers, All On)

| Service | Provider | Toggle in `tilt-settings.json` | Constraint |
|---------|----------|--|--|
| PostgreSQL (auth-service) | Supabase | `use_cloud_postgres` | — |
| Redis (caches/rate-limit/dedup) | Upstash | `use_cloud_redis` | **500k commands/month** (16.6k/day) |
| MongoDB (6 databases) | Atlas M0 | `use_cloud_mongo` | — |
| Kafka | Aiven (paid tier) | `use_cloud_kafka` | Topics unrestricted |
| Object Storage | Cloudflare R2 | `use_cloud_storage` | — |
| Metrics/Logs/Traces | Grafana Cloud | `use_grafana_cloud` | **10k active series** |
| CI | GitHub Actions | `.github/workflows/` | Student Pack |

**In-cluster stacks still testable by flipping toggles.** Email (Brevo) has no in-cluster fallback — it replaced the old MailHog in-cluster fake and always goes external.

**Important constraints affecting code:**
- **Upstash Redis:** 500k commands/month. Rate limiting + JWT blacklist + 3 caches share it. Don't add per-request Redis calls lightly.
- **Grafana Cloud:** 10k active series. Micrometer's bucket histograms dropped in `alloy-cloud-values.yaml` to stay under. High-cardinality metrics (per-user, per-URI labels) consume budget fast.

---

## Development Workflows

### Branch Naming

```
feature/<kebab-case>        # feature/add-payment-service
fix/<kebab-case>            # fix/auth-token-expiry
refactor/<kebab-case>       # refactor/remove-cloud-config
chore/<kebab-case>          # chore/update-dependencies
```

Rename before first commit:
```bash
git branch -m <type>/<short-description>
git push -u origin <new-branch-name>
```

### Local Dev with docker-compose

```bash
# Start infrastructure only
docker compose -f docker-compose/qa/docker-compose.yml up -d

# Start individual service (Maven)
./mvnw spring-boot:run -pl movie-service -Dspring-boot.run.profiles=dev

# Start individual service (Gradle, from inside service dir)
cd api-gateway && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Tilt Deployment (k3s)

```bash
# Start entire local cluster (all services + infra + observability)
tilt up

# Stop
tilt down

# View logs (Tilt UI at http://localhost:10350)
```

### CI Workflow

One path-filtered workflow per service in `.github/workflows/`. Each service triggers only its own workflow. **12 services, 12 workflows** — adding a new service requires adding its workflow or it is never built.

**Maven vs Gradle workflows differ significantly:**
- Maven: Setup parent POM first (`./mvnw -B -q install -N`), then `./mvnw -B verify -pl <svc> -am`
- Gradle: No parent step, build with `./gradlew build --no-daemon` from service directory, context must be service dir (`.`)

---

## Key Files to Know

| File | Purpose | Services |
|------|---------|----------|
| CLAUDE.md | Comprehensive dev guide (build systems, ports, architecture, deployment) | All |
| api-gateway/DESIGN.md | API Gateway routing, filters, and configuration decisions | api-gateway |
| auth-service/DESIGN.md | Auth flow, JWT lifecycle, OTP, outbox pattern | auth-service |
| `src/main/resources/application-dev.yml` | Standalone config (routes, Kafka topics, etc.) | All |
| `docker-compose/qa/docker-compose.yml` | Local infrastructure (Postgres, Mongo, Redis, Kafka, etc.) | All |
| `k8s/infra/README.md` | Local Kubernetes setup (Tilt) | All |
| `pom.xml` | Maven parent POM, modules list, plugin versions | Maven services |
| `<svc>/build.gradle` | Gradle build config | api-gateway, auth-service |

---

## Common Patterns

**Exception Handling:** Every service has `GlobalExceptionHandler` + custom exception types in `exception/` package. Follow the same structure when adding new exceptions.

**Auditing:** Base entity classes (`BaseEntity`, `AuditableEntity`) in `common/audit/` include `createdAt`, `updatedAt`, `createdBy`, `modifiedBy`. Always extend them for domain entities.

**Caching:** Redis-backed (Upstash free tier). Use Spring `@Cacheable`, `@CacheEvict`. Be mindful of the 500k command/month budget.

**Testing:** JUnit 5, Mockito, Testcontainers. Unit tests in `*Test.java`, integration tests use `@SpringBootTest` with Testcontainers.

---

## Before Every Task

1. **Identify scope:** Does it touch api-gateway/auth-service (Gradle) or others (Maven)?
2. **Check existing pattern:** Search for similar code in `config/`, `common/`, or `service/` directories within the target service.
3. **Namespace consistency:** Use `vn.iotstar.<service>` for all code.
4. **Duplication intentional:** If touching cross-service code (exceptions, Kafka events, topic names), update EVERY affected service.
5. **Refer to DESIGN.md:** Before changing routing, filters (api-gateway) or auth flow (auth-service), read their DESIGN.md files.

---

## Questions?

Refer to CLAUDE.md for deeper context, or read the DESIGN.md file for the specific service you're modifying.

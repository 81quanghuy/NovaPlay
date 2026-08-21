# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Branch Naming Convention

At the start of every session, rename the working branch before making any commits:

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<kebab-case>` | `feature/add-payment-service` |
| Fix | `fix/<kebab-case>` | `fix/auth-token-expiry` |
| Refactor | `refactor/<kebab-case>` | `refactor/remove-cloud-config` |
| Chore | `chore/<kebab-case>` | `chore/update-dependencies` |

```bash
git branch -m <type>/<short-description>
git push -u origin <new-branch-name>
```

## Two Build Systems — check before running any command

The repo is **not** a single build. `api-gateway` and `auth-service` were migrated off Maven and are now **standalone Gradle builds** with their own wrapper, their own `settings.gradle`, and a *different platform baseline*. They are deliberately **absent from the root `pom.xml` `<modules>` list**.

| | Root Maven aggregator | `api-gateway`, `auth-service` |
|---|---|---|
| Build file | `pom.xml` (+ `<module>` per service) | `<svc>/build.gradle` |
| Wrapper | `./mvnw` at repo root | `./gradlew` **inside the service dir** |
| Java | 21 | 25 (toolchain) |
| Spring Boot | 3.5.0 | 4.1.0 |
| Spring Cloud | 2025.0.0 | 2025.1.2 |

A change that touches both sides must be validated against both baselines — the Gradle pair is a version generation ahead.

```bash
# --- Maven side (movie, notification, payment, promotion, streaming,
#     user, report, media, config, transcoding-worker) ---
./mvnw clean install -DskipTests          # all Maven modules
./mvnw clean install -pl movie-service -DskipTests
./mvnw spring-boot:run -pl <module> -Dspring-boot.run.profiles=dev
./mvnw test -pl <module>
./mvnw test -pl <module> -Dtest=SomeTest#someMethod   # single test

# --- Gradle side (run from inside the service directory) ---
cd api-gateway && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd auth-service && ./gradlew test --tests 'SomeTest.someMethod'
```

Building a Maven module **alone** (not via the aggregator) requires the parent POM in the local repo first — this is what per-service CI does:

```bash
./mvnw -B -q install -N        # installs only the parent pom, builds no module
./mvnw -B -pl <module> verify
```

`transcoding-worker` needs `ffmpeg`/`ffprobe` on `PATH`; no other service does.

**Startup order (local dev):**
1. Infrastructure: `docker compose -f docker-compose/qa/docker-compose.yml up -d`
2. `api-gateway` (port 8072)
3. Other services in any order

## Service Port Map

| Service | Port | Build | Backing stores |
|---------|------|-------|----------------|
| api-gateway | 8072 | Gradle | Redis |
| auth-service | 8000 | Gradle | PostgreSQL + Redis + Kafka (outbox producer) |
| user-service | 8700 | Maven | MongoDB + Kafka (consumer) |
| movie-service | 8600 | Maven | MongoDB + Redis (cache) |
| notification-service | 8900 | Maven | MongoDB + Redis (dedup) + Kafka (consumer) |
| media-service | 8081 | Maven | MongoDB + Kafka (producer) + object storage |
| streaming-service | 8200 | Maven | MongoDB + Redis (cache) + object storage — **no Kafka** |
| transcoding-worker | 8400 | Maven | Kafka (consumer) + object storage — không DB riêng |
| promotion-service | 8300 | Maven | PostgreSQL + Kafka (outbox producer) — chưa route qua gateway |
| config-service | 8500 | Maven | MongoDB |
| payment-service | — | Maven | scaffold rỗng (chỉ `@SpringBootApplication`) |
| report-service | — | Maven | scaffold rỗng (chỉ `@SpringBootApplication`) |
| Kafka UI | 8080 | — | — |
| MailHog (SMTP giả, QA) | 1025 / 8025 | — | — |
| Grafana | 3000 | — | — |
| Prometheus | 9090 | — | — |

`payment-service` and `report-service` are placeholders — they have no controllers, no config beyond a profile marker, and no port. Don't treat them as working services.

`api-gateway` and `auth-service` each carry a `DESIGN.md` next to `build.gradle`; read it before changing routing, filter order, or token handling in those two.

## Architecture Overview

**Maven multi-module monorepo + two detached Gradle services** (see the build table above).

### Request Flow

```
React (Vite) → API Gateway :8072 → Downstream Services (k8s Service DNS)
```

The **API Gateway** (`api-gateway`) is the single entry point:
- `AuthenticationFilter` (GlobalFilter, order `HIGHEST_PRECEDENCE+1`) verifies JWTs before any route filter runs
- On success, strips client-supplied `X-User-Email`/`X-User-Roles` headers and re-injects them from JWT claims — downstream services trust these headers without re-verifying JWT
- Routes, CircuitBreaker, Retry, and RequestRateLimiter are configured in `application-dev.yml` (YAML-only, no `RouteLocator` bean)
- Rate limiting keyed by `X-User-Email` (authenticated) or IP (anonymous), backed by Redis


### Auth Flow

`auth-service` owns identity: RSA private key signs JWTs, RSA public key is loaded by `api-gateway` for verification. Token blacklist (logout/revoke) is stored in Redis under `jwt:blacklist:<jti>`.

Email notifications (OTP, password reset) use the **Transactional Outbox pattern**: `auth-service` writes to an `OutboxEvent` table, `OutboxRelayJob` publishes to Kafka, `notification-service` consumes.

### Notifications

`notification-service` is the single consumer for all user-facing notifications (it absorbed the former `email-service`). It consumes `send-email.v1`, `activate-account.v1` and `notification.requested.v1`, then fans out to channels:

- **Email** — SMTP + Thymeleaf, i18n via `messages*.properties`
- **In-app** — MongoDB document + REST API at `/api/v1/notifications` (per-user; identity from the `X-User-Email` gateway header)

Adding a channel means adding one `NotificationChannel` bean plus an entry in `ChannelRoutingPolicy`; the dispatcher and consumers stay untouched. Delivery is at-least-once with **per-channel dedup keys** in Redis, so a retry only re-sends the channel that actually failed. Topic names live in `vn.iotstar.notificationservice.util.TopicNames` — each service keeps its own copy of this constants class.

### Video Pipeline: upload → transcode → playback

Three services form one chain, joined by Kafka topics and a shared object-storage bucket — no service reads another's database.

```
browser ──presigned PUT──> object storage (MinIO dev / R2 / B2)
   │
media-service  ──video-source-ready.v1──>  transcoding-worker
   │                                            │ ffmpeg → HLS ladder + AES-128
   │                                            │ writes segments/playlists to storage
   │           <──Feign: manifest complete/fail──┘
   └──video-transcode-completed.v1──> (fan-out)

streaming-service ──Feign──> media-service (manifest) / movie-service / user-service (entitlement)
                  ──reads segments from storage──> serves HLS to player
```

- **media-service** owns upload (`/api/v1/media/upload/**`: single presigned PUT plus a full multipart init/part-url/complete/abort flow) and the `VideoManifest` lifecycle (`/api/v1/media/video-manifests/**`, including `/{id}/retry`). It is the only Kafka producer in the chain.
- **transcoding-worker** consumes `video-source-ready.v1`, shells out to `ffmpeg`/`ffprobe`, and reads/writes storage **directly with real credentials** — presigned URLs are for browsers only. It reports back over Feign, not Kafka. Failures land on `video-source-ready.v1.DLT`.
- **streaming-service** never transcodes; it resolves entitlement + manifest (both Redis-cached: `CachedEntitlementResolver`, `CachedManifestResolver`) and streams the worker's output.

Storage is provider-abstracted (`StorageProvider` / `StorageProviderProperties`) across `aws-s3` (MinIO in dev, `localhost:9010`, bucket `novaplay-media-dev`), `cloudflare-r2`, and `backblaze-b2`. All three services must point at the **same** bucket or playback breaks.

### Playback Security (streaming-service)

Two mechanisms that are easy to confuse with the login JWT — neither is one:

- **Playback token** (`PlaybackTokenService`) — a short-lived HMAC JWT (`playback.token-secret`, default 4h TTL) proving "this user may play *this* manifest". `/api/v1/streaming/hls/**` is `permitAll` in Spring Security precisely because HLS players cannot attach an `Authorization` header; the playback token is what actually guards those bytes.
- **HLS key wrapping** — segments are AES-128 encrypted. `transcoding-worker`'s `KeyWrapService` and `streaming-service`'s `KeyUnwrapService` are inverses sharing one credential, `TRANSCODE_KEY_WRAP_SECRET` (AES-GCM). That's a shared *secret*, not shared code. Unwrapped keys are never cached or logged.

### Inter-Service Communication

Services call each other via **OpenFeign**, resolved via explicit URL (k8s Service DNS name, injected through `services.<name>` config property). Example: `user-service` calls `media-service` via `MediaServiceClient` in `service/client/`.

Two request-scoped identities travel over Feign, and picking the wrong one is a live bug source:

| Feign config | Identity sent | Use for |
|---|---|---|
| `FeignSecurityConfig` | relays the **caller's** `X-User-Email` / `X-User-Roles` | owner-gated calls (user's own data) |
| `ServiceIdentityFeignConfig` | hardcoded `system@<service>` + `[ROLE_SERVICE]` | machine-to-machine reads that must bypass owner gating |

Both are **intentionally not annotated `@Configuration`** — annotating them would let component-scan apply one globally to every Feign client. Attach them per-client via `@FeignClient(configuration = ...)`.

### Gateway-Only Enforcement

Every downstream service carries a private copy of `GatewayAuthFilter` (`config/security/`), which rejects requests lacking a valid `X-Gateway-Auth` shared secret — this is how a service knows the `X-User-Email` header it trusts really came from the gateway. Controlled by `application.security.gateway-secret.{enabled,value}`; **disabled in dev** so services can be called directly, **mandatory in prod** (the filter fails startup if enabled without a secret). It only answers "did this come through the gateway?" — it says nothing about *who* the user is, which is why `/hls/**` needs the separate playback token.

`HeaderAuthenticationFilter` then converts the gateway headers into a Spring `Authentication`.

### Key Configuration Patterns

Each service has:
- `application.yml` — only `spring.application.name`
- `application-dev.yml` — full standalone config

Activate dev profile via `SPRING_PROFILES_ACTIVE=dev` or `-Dspring.profiles.active=dev`.

Dev configs commit **placeholder** secrets on purpose (e.g. `transcode.key-wrap-secret` is a fixed base64 of 32 known bytes so every dev machine produces identical output). Prod overrides them via env vars in `application-prod.yml` — never promote a dev default.

RSA keys location:
- `auth-service`: `src/main/resources/keys/private.pem` + `public.pem`
- `api-gateway`: `src/main/resources/certs/public.pem`

### No Shared Library

There is **no shared `utils` artifact** — it was removed deliberately. Every service owns a private copy of the code it needs (`GenericResponse`, `GlobalExceptionHandler` + exception types, auditing base classes, Kafka event records, and the outbox package), each under its own `vn.iotstar.<service>` namespace:

| Concern | Location in every service |
|---------|---------------------------|
| Response wrapper | `<svc>.common.GenericResponse` |
| Auditing base classes | `<svc>.common.audit.*` |
| Kafka event records | `<svc>.common.dto.*` |
| Exception handler + types | `<svc>.exception.*` |
| Topic name constants | `<svc>.util.TopicName(s)` |
| Transactional outbox | `<svc>.outbox.*` (auth-service, promotion-service) |

Duplication across services is intentional: it's what lets each service build, version, and deploy on its own. **Do not reintroduce a shared module.** When a change touches a duplicated type, apply it to each service that needs it — and only those.

Because copies are independent, a Kafka event's class name is a *local* detail. Cross-service topics must not rely on Jackson's `__TypeId__` FQCN header: use `JsonSerializer.TYPE_MAPPINGS` / `JsonDeserializer.TYPE_MAPPINGS` with a logical name (see `send-status-media.v1` between media-service and user-service), or disable type headers and declare the target type explicitly (see notification-service).

Note the naming inconsistency: most services use `util/TopicNames.java`, but `user-service` uses `util/TopicName.java` (singular). Grep both.

### Kafka Topics

Topic strings are duplicated per service, so a rename means editing every copy. Current catalog:

| Topic | Producer → Consumer |
|---|---|
| `send-email.v1` | auth-service (outbox) → notification-service |
| `activate-account.v1` | auth-service (outbox) → notification-service |
| `notification.requested.v1` | any → notification-service |
| `send-status-media.v1` | media-service → user-service |
| `video-source-ready.v1` | media-service → transcoding-worker |
| `video-transcode-completed.v1` | media-service → (fan-out) |
| `create-referral.v1`, `qualify-referral.v1`, `redeem-coupon.v1` | promotion-service (outbox) |

Failed messages go to `<topic>.DLT` (`TopicNames.dltOf(...)`); notification-service has a dedicated `DeadLetterConsumer` on a no-retry container factory.

## Deployment

- `docker-compose/qa/` — full local/QA stack (infra + observability), with `docker-compose/secrets/` and `docker-compose/init-db/` alongside.
- `k8s/<service>/` — per-service `deployment.yaml`, `service.yaml`, `configmap.yaml`, `secret.example.yaml`, plus `hpa.yaml` + `networkpolicy.yaml` where relevant. `k8s/infra/` holds Helm values for PostgreSQL, Redis, and Kafka plus seed SQL.
- Images are built with the **jib-maven-plugin** as `novaplay/<artifactId>:v<version>`, configured in the root `pom.xml` `pluginManagement`.

## CI

`.github/workflows/` holds **one path-filtered workflow per service** (media, movie, notification, streaming, transcoding-worker, user) — a change under `streaming-service/**` or to the root `pom.xml` triggers only that service's build. Adding a new Maven service means adding its workflow; there is no catch-all job, and `api-gateway`/`auth-service` currently have none.

Each workflow runs `./mvnw -B -q install -N` before building the module, because per-service builds still resolve the root parent POM.

## Observability

Full stack in `docker-compose/qa/`: Prometheus → Grafana (port 3000), Loki (write/read/backend) via Alloy collector, Tempo for distributed tracing. Services use OpenTelemetry Java agent (`opentelemetry-javaagent-2.11.0.jar`). Trace context propagated automatically via OTEL.

## Repositories

- **Backend:** `81quanghuy/NovaPlay`
- **Frontend:** `81quanghuy/NovaPlay_FE`

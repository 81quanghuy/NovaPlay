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

Email notifications (OTP, password reset) use the **Transactional Outbox pattern**: `auth-service` INSERTs into `outbox_events` inside the business transaction, and after COMMIT a `TransactionSynchronization` hook hands the row id straight to an in-process relay pool — publisher and relay are the same JVM, so no cross-process wake-up is involved. `OutboxSweepJob` re-claims orphans every 60s (`FOR UPDATE SKIP LOCKED`, so pods share the work); `notification-service` consumes.

The `outbox_notify_trg` trigger still fires `pg_notify`, but **nothing listens by default** (`novaplay.outbox.listen-enabled=false`). LISTEN/NOTIFY cannot work through a connection pooler — Supavisor/PgBouncer inject `ParameterStatus` onto the idle connection and pgjdbc's `processNotifies` only handles `'A'/'E'/'N'`, so it dies with `Unknown Response Type S` in a reconnect loop. Only turn the flag on if `DATASOURCE_URL` points straight at Postgres. Delivery is at-least-once: the row is deleted only after Kafka acks.

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

Storage is provider-abstracted (`StorageProvider` / `StorageProviderProperties`) across `aws-s3` (MinIO fallback at `localhost:9010`), `cloudflare-r2`, and `backblaze-b2` (declared, never used). **`cloudflare-r2` is what actually runs.** All three services must point at the **same** bucket or playback breaks — media signs the upload URL, the worker writes HLS output, streaming reads segments back, and a bucket mismatch surfaces only as a 404 at playback with no error in between.

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

On Aiven, the `NewTopic` beans create these at startup — but only for services that actually run in the cluster, so the three promotion-service topics do not exist. The beans hardcode `.replicas(1)`; if Aiven's plan rejects that, topic creation fails silently in the log (see `k8s/infra/README.md` §3.3).

## Deployment

### There is no server — the cluster is local Docker only

**Kubernetes here runs inside Docker Desktop on the developer's laptop (k3s provisioner), and nowhere else.** There is no VPS, no public IP, no domain pointing anywhere, no environment a user outside this machine can reach. This is a budget constraint (student, no paid hosting), not a stage the project is passing through — do not plan, suggest, or write code that assumes a deployed environment exists.

Consequences that change how to read this repo:

- `k8s/overlays/prod/` (`ingress.yaml`, `clusterissuer.yaml`, `ghcr.io/81nhuquynh/<svc>:<sha>` image pins) and `k8s/infra/README-prod.md` describe **a cluster that does not exist**. They are a design exercise: `kubectl apply -k k8s/overlays/prod` has never run for real, DNS for `81quanghuy.io.vn` resolves to nothing, and cert-manager / Let's Encrypt / SealedSecrets are unexercised paths. Keep them internally coherent; never cite them as "how it's deployed" or debug against them.
- The **only** deploy path that actually executes is `tilt up` against the local cluster — see `k8s/infra/README.md`. Entry point `http://localhost` (api-gateway), Tilt UI `http://localhost:10350`.
- CI builds and tests only. **There is no CD.** Nothing pushes to any cluster.
- `hpa.yaml` / `pdb.yaml` / resource requests under `k8s/<svc>/` were sized for "1–2 VPS, ~8 vCPU" (see the comment headers). On one laptop they over-commit: `Pending` pods with 9 services + Kafka + monitoring up at once is the manifests being too generous, not a bug. Set `use_monitoring: false` in `tilt-settings.json` first.
- NetworkPolicy behaviour differs per provisioner: Docker Desktop's **kubeadm** cluster uses kindnet and silently ignores NetworkPolicy, while the **k3s** provisioner ships kube-router and does enforce it. Confirm which one the cluster was created with before concluding a policy "works" — a missing rule is invisible under kindnet.
- If public access is ever needed (demo, webhook callback), the answer is a tunnel from this laptop (Cloudflare Tunnel / ngrok), not a deployment. Nothing about the cluster changes.

### Managed cloud services in use (free tiers)

The local cluster deliberately talks to real managed datastores instead of in-cluster pods, toggled in `tilt-settings.json` and credentialed in `k8s/infra/dev-secrets.env` (both gitignored):

| Concern | Provider | Toggle in `tilt-settings.json` |
|---|---|---|
| PostgreSQL (auth-service) | Supabase | `use_cloud_postgres` + `cloud_postgres_url` |
| Redis (all cache/rate-limit/dedup) | Upstash | `use_cloud_redis`, `cloud_redis_host`, `cloud_redis_ssl_enabled` |
| MongoDB (6 databases) | Atlas M0 | `use_cloud_mongo` + each `*__MONGODB_URI` |
| Kafka | Aiven (paid plan — topics unrestricted) | `use_cloud_kafka` + `cloud_kafka_bootstrap` |
| Object storage | Cloudflare R2 | `use_cloud_storage` + `*__R2_*` |
| Metrics / logs / traces | Grafana Cloud | `use_grafana_cloud` (Alloy stays in-cluster as the only exporter) |
| CI | GitHub Actions (Student Pack) | `.github/workflows/` |

Every toggle above is currently **on**. Only `mailhog` still runs in-cluster as a real dependency; the in-cluster Postgres/Redis/Kafka/Mongo/MinIO paths and the in-cluster Prometheus/Loki/Tempo stack are all still wired and testable by flipping the toggle back off.

**The free tiers impose constraints that change how code gets written here:**

- **Upstash Redis free = 500k commands/month (~16.6k/day).** Rate limiting, JWT blacklist checks, and three caches all share it. Don't add a per-request Redis call without thinking about that budget.
- **Grafana Cloud free = 10k active series.** `alloy-cloud-values.yaml` drops Micrometer's `*_seconds_bucket` histograms to stay under it; adding high-cardinality metrics (anything labelled per-user or per-URI) eats the budget fast.

The Aiven plan is a paid one, so topics are **not** rationed: `KAFKA_ADMIN_AUTO_CREATE=true` lets each service's `NewTopic` beans create their topics (all 11, DLTs included) via `AdminClient.createTopics` at startup. That is explicit creation — Aiven's broker-level `auto_create_topics_enable` stays false — so a topic with no `NewTopic` bean still won't exist.

Kafka connection settings live in each service's `application-prod.yml` and default to PLAINTEXT, so docker-compose and Testcontainers are unaffected; `SASL_SSL` + SCRAM-SHA-256 is switched on by env. The Aiven CA is mounted from the shared `aiven-kafka-ca` Secret at `/etc/novaplay/kafka/ca.pem` — without it the failure is an `SSLHandshakeException`, not an auth error.

R2 is selected through the OpenFeature flag `media-storage-provider` (default from `DEFAULT_STORAGE_PROVIDER`); a value stored in config-service's Mongo **overrides** the env default. Only new uploads follow the flag — existing `Media` records keep the provider they were written with.

Because the datastores are off-machine: the laptop's IP must be allowlisted at each provider, and no internet = the cluster is broken in ways that look like application bugs (connection timeouts at startup, Liquibase hanging, `LISTEN/NOTIFY` silently dead).

### Layout

- `docker-compose/qa/` — full local/QA stack (infra + observability), with `docker-compose/secrets/` and `docker-compose/init-db/` alongside. Independent of the k8s path; never run both (port conflicts).
- `k8s/<service>/` — per-service `deployment.yaml`, `service.yaml`, `configmap.yaml`, `secret.example.yaml`, plus `hpa.yaml` + `networkpolicy.yaml` where relevant. `k8s/infra/` holds Helm values for PostgreSQL, Redis, and Kafka plus seed SQL.
- Images are built with the **jib-maven-plugin** as `novaplay/<artifactId>:v<version>`, configured in the root `pom.xml` `pluginManagement`.

## CI

`.github/workflows/` holds **one path-filtered workflow per service** — a change under `streaming-service/**` triggers only that service's build. There is no catch-all job, so a new service means adding its workflow or it is never built. All 12 services are now covered.

The two build systems need **different** workflows, and copying the wrong template is the usual mistake:

| | Maven services (10) | `api-gateway`, `auth-service` |
|---|---|---|
| JDK | 21 | **25** (`build.gradle` toolchain) |
| Setup cache | `cache: maven` | `cache: gradle` |
| Parent POM step | `./mvnw -B -q install -N` first — per-service builds still resolve the root parent POM | none; standalone build |
| Build | `./mvnw -B verify -pl <svc> -am` | `./gradlew build --no-daemon` in `working-directory: <svc>` |
| `paths` filter | includes root `pom.xml` | **excludes** it — not in `<modules>` |
| Test reports | `<svc>/target/*-reports/` | `<svc>/build/{reports/tests,test-results}/test/` |
| Docker context | repo root (`-f <svc>/Dockerfile .`) | **the service dir** (`-f <svc>/Dockerfile <svc>`) |

The Docker context difference is load-bearing: the Gradle Dockerfiles `COPY gradlew`/`settings.gradle` by relative path and each ships its own `.dockerignore`, so a root context fails immediately.

`promotion-service`, `payment-service` and `report-service` have **no `docker-build` job** — they have no Dockerfile. No workflow pushes an image anywhere; there is still **no CD**.

## Observability

Two separate stacks, depending on how the app is running:

- **docker-compose (`docker-compose/qa/`)** — Prometheus → Grafana (port 3000), Loki via Alloy, Tempo. Unchanged.
- **k8s (Tilt)** — `use_grafana_cloud: true` means nothing but **Alloy** runs in-cluster; it scrapes `/actuator/prometheus`, tails pod logs, receives OTLP, and ships all three to Grafana Cloud. Dashboards live on grafana.net, not `localhost:3000`. Flipping the toggle off restores the full in-cluster stack. Either way services send OTLP to `alloy.monitoring.svc:4318` — never straight to Tempo.

Services use the OpenTelemetry Java agent (`opentelemetry-javaagent-2.11.0.jar`); trace context propagates automatically.

## Repositories

- **Backend:** `81quanghuy/NovaPlay`
- **Frontend:** `81quanghuy/NovaPlay_FE`

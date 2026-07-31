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

## Build & Run

```bash
# Build all modules
./mvnw clean install -DskipTests

# Build a single module
./mvnw clean install -pl api-gateway -DskipTests

# Run a single service (dev profile)
./mvnw spring-boot:run -pl <module-name> -Dspring-boot.run.profiles=dev

# Run tests for a single module
./mvnw test -pl <module-name>

# Run all tests
./mvnw test
```

**Startup order (local dev):**
1. Infrastructure: `docker compose -f docker-compose/qa/docker-compose.yml up -d`
2. `api-gateway` (port 8072)
3. Other services in any order

## Service Port Map

| Service | Port | Database |
|---------|------|----------|
| api-gateway | 8072 | Redis |
| auth-service | 8000 | PostgreSQL + Redis + Kafka |
| user-service | 8700 | MongoDB + Kafka (consumer) |
| movie-service | 8600 | MongoDB + Redis (cache) |
| notification-service | 8900 | MongoDB + Redis (dedup) + Kafka (consumer) |
| Kafka UI | 8080 | — |
| MailHog (SMTP giả, QA) | 1025 / 8025 | — |
| Grafana | 3000 | — |
| Prometheus | 9090 | — |

## Architecture Overview

**Maven multi-module monorepo.** Root `pom.xml` defines all modules. Java 21, Spring Boot 3.5, Spring Cloud 2025.0.0.

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

Adding a channel means adding one `NotificationChannel` bean plus an entry in `ChannelRoutingPolicy`; the dispatcher and consumers stay untouched. Delivery is at-least-once with **per-channel dedup keys** in Redis, so a retry only re-sends the channel that actually failed. Topic names live in `vn.iotstar.utils.constants.TopicNames`.

### Inter-Service Communication

Services call each other via **OpenFeign**, resolved via explicit URL (k8s Service DNS name, injected through `services.<name>` config property). Example: `user-service` calls `media-service` via `MediaServiceClient` in `service/client/`.

### Key Configuration Patterns

Each service has:
- `application.yml` — only `spring.application.name`
- `application-dev.yml` — full standalone config

Activate dev profile via `SPRING_PROFILES_ACTIVE=dev` or `-Dspring.profiles.active=dev`.

RSA keys location:
- `auth-service`: `src/main/resources/keys/private.pem` + `public.pem`
- `api-gateway`: `src/main/resources/certs/public.pem`

### `utils` Module

Shared library (`vn.iotstar:utils:0.0.1`) included by other services. Contains shared DTOs, common exceptions, and base response wrappers. Always build this first when modifying shared types: `./mvnw install -pl utils`.

## Observability

Full stack in `docker-compose/qa/`: Prometheus → Grafana (port 3000), Loki (write/read/backend) via Alloy collector, Tempo for distributed tracing. Services use OpenTelemetry Java agent (`opentelemetry-javaagent-2.11.0.jar`). Trace context propagated automatically via OTEL.

## Repositories

- **Backend:** `81quanghuy/NovaPlay`
- **Frontend:** `81quanghuy/NovaPlay_FE`

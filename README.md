# NovaPlay — Microservices Movie Streaming Platform

> A production-style, microservices-based online movie platform built with Spring Boot and React. NovaPlay demonstrates API Gateway, service discovery, centralized configuration, **synchronous inter-service communication (OpenFeign)**, and modern DevOps practices (Docker, Observability). **Kafka is used only for email workflows.**

<p align="center">
  <img alt="NovaPlay Architecture" src="https://user-images.githubusercontent.com/placeholder/nova-arch-diagram.png" />
</p>

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Quick Start (Local)](#quick-start-local)
- [Configuration](#configuration)
- [Security & Auth](#security--auth)
- [Synchronous Communication (OpenFeign)](#synchronous-communication-openfeign)
- [Asynchronous Messaging (Email only)](#asynchronous-messaging-email-only)
- [Databases & Storage](#databases--storage)
- [Observability](#observability)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Deployment Notes](#deployment-notes)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Maintainer](#maintainer)

---

## Overview

NovaPlay là hệ thống xem phim online theo kiến trúc **microservices**. Dự án tập trung vào các thực hành hiện đại:
- API Gateway và Service Discovery
- Tách biệt cấu hình qua Config Server
- **Giao tiếp đồng bộ giữa các services bằng OpenFeign (HTTP/REST)**
- **Kafka chỉ dùng cho email flow (đăng ký, quên mật khẩu, v.v.)**
- Bảo mật theo chuẩn JWT/OAuth2, rate-limit, CORS
- Quan sát hệ thống (metrics, logs, traces)
- Orchestration với Docker Compose cho môi trường dev

Mục tiêu: **hệ thống mẫu hoàn chỉnh** để học, demo portfolio, và có thể mở rộng thành sản phẩm thực tế.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              React Frontend (5173)                         │
└───────────────▲────────────────────────────────────────────────────────────┘
                │ HTTP (CORS/JWT)
                ▼
        ┌───────────────────────────────────┐
        │        API Gateway (8072)         │
        │  - routing, CORS, rate-limit      │
        │  - Swagger aggregation            │
        └───────────────▲───────────────────┘
                        │
                        │ service discovery
                        ▼
        ┌───────────────────────────────────┐
        │      Discovery Server (Eureka)    │
        └────────────────▲──────────────────┘
                         │
                         │         REST via OpenFeign
           ┌─────────────┼──────────────────────────────────────────────────┐
           │             │                                                  │
           ▼             ▼                                                  ▼
  ┌──────────────┐ ┌───────────────┐  ...  ┌────────────────┐     ┌─────────────────┐
  │ auth-service │ │ user-service  │       │ movie-service  │ ... │ streaming-serv. │
  └──────▲───────┘ └──────▲────────┘       └──────▲─────────┘     └────────▲────────┘
         │ JWT/OAuth2      │ users/profile          │ movies, genres            │ HLS/DASH
         │                 │                        │ search/filter             │ edge/Nginx
         │                 │                        │                           │
         │                 │                        │
         │                 │                        │
         │                 │                        │
         │                 │                        │
         ▼                 ▼                        ▼
   ┌────────────────────────────────────────────────────────────────────────────┐
   │                     Kafka (Email flows ONLY)                               │
   │   Producers: auth-service, user-service → Topic(s) for email               │
   │   Consumer:  email-service                                                 │
   └────────────────────────────────────────────────────────────────────────────┘

   ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────────┐
   │ PostgreSQL (user)  │  │ MongoDB (movie)    │  │ MySQL (payment/promo)│
   └────────────────────┘  └────────────────────┘  └──────────────────────┘

   ┌──────────────────────────────────────┐
   │ Config Server (cloud-config)         │
   │ - centralized application.yml        │
   └──────────────────────────────────────┘
```

> **Note:** Giao tiếp chính giữa các service là **Feign over HTTP**; Kafka chỉ kích hoạt luồng email nhằm giảm độ phức tạp, giữ được tính phản hồi nhanh cho các API chính.

---

## Services

| Service               | Port (dev) | Database          | Responsibilities |
|-----------------------|------------|-------------------|------------------|
| `api-gateway`         | `8072`     | —                 | Routing, CORS, rate-limit, Swagger aggregation |
| `discovery-server`    | `8761`     | —                 | Eureka registry |
| `cloud-config`        | `8888`     | Git repo          | Centralized config |
| `auth-service`        | `8000`     | PostgreSQL/Redis* | Auth, JWT issuance, OTP/email trigger (produce Kafka) |
| `user-service`        | `8001`     | PostgreSQL        | User profile, preferences (Feign clients) |
| `movie-service`       | `8002`     | MongoDB           | Movies, genres, casts, search |
| `streaming-service`   | `8003`     | — / ObjectStore   | HLS/DASH streaming, signed URLs |
| `media-service`       | `8004`     | ObjectStore       | Media ingestion, posters, thumbnails |
| `email-service`       | `8005`     | —                 | **Kafka consumer**; SMTP provider integration |
| `notification-service`| `8006`     | Redis/PostgreSQL  | (Optional) In-app notifications via **Feign** |
| `payment-service`     | `8007`     | MySQL/PostgreSQL  | Payment sessions, webhooks (Feign to user/order) |
| `promotion-service`   | `8008`     | MySQL/PostgreSQL  | Coupons, campaigns (Feign) |
| `report-service`      | `8009`     | DWH (OLAP)        | Analytics, aggregates |
| `init-db` (folder)    | —          | —                 | DB schema/seed scripts |
| `utils` (folder)      | —          | —                 | Common libraries/utilities |

\* Redis dùng cho rate-limit, captcha/OTP, token blacklist, hoặc cache.

---

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Cloud (Gateway, Config, Eureka), Spring Data (JPA/Mongo), Spring Security.
- **Inter-service:** **OpenFeign** (+ Resilience4j for timeouts/retries/circuit breakers).
- **Async (Email only):** Apache Kafka (KRaft) cho các sự kiện gửi email.
- **Databases:** PostgreSQL, MongoDB, MySQL; Data Warehouse cho reporting.
- **Cache/Infra:** Redis, Object Storage (MinIO/S3).
- **Frontend:** React + TypeScript, Tailwind CSS (repo FE riêng).
- **Observability:** OpenTelemetry, Grafana Tempo (traces), Prometheus + Grafana (metrics), Loki (logs).
- **DevOps:** Docker & Docker Compose, Maven, GitHub Actions (CI) _[optional]_.

---

## Quick Start (Local)

### 1) Prerequisites
- Docker Desktop 4.x+
- JDK 21, Maven 3.9+ (nếu chạy local không dùng containers)
- Node 20+ (cho frontend)

### 2) Clone & cấu hình
```bash
git clone https://github.com/81quanghuy/NovaPlay.git
cd NovaPlay
```

### 3) Build các service
```bash
mvn -T 1C -DskipTests clean package
```

### 4) Khởi chạy bằng Docker Compose
```bash
docker compose up -d
docker compose logs -f api-gateway
```
Ports mặc định:
- Gateway: `http://localhost:8072`
- Discovery: `http://localhost:8761`
- Config: `http://localhost:8888`
- Auth: `http://localhost:8000`
- Kafka (email only): `localhost:9092`

### 5) Truy cập nhanh
- Swagger qua Gateway (aggregated): `http://localhost:8072/swagger-ui.html` (hoặc `/swagger-ui/index.html` tùy config)
- React FE (dev): `http://localhost:5173`

> **Troubleshooting nhanh**
> - **CORS**: kiểm tra CORS config ở **Gateway** (Origin FE `http://localhost:5173`).
> - **Feign**: xác thực `@EnableFeignClients`, khai báo `url`/`name` trùng với serviceId trên Eureka; set **timeouts** & **retry** qua properties.
> - **Kafka (Email)**: đảm bảo `KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092` cho client ngoài container.
> - **Email (SMTP)**: dùng provider thật (Mailtrap/SendGrid/SES) thay vì `localhost:25`.

---

## Configuration

- Mọi cấu hình nằm ở repo **cloud-config** (application.yml theo từng service/profile).
- Service khi start sẽ đọc từ `cloud-config` (port `8888`). Cần set:
  - `spring.cloud.config.uri`
  - `spring.application.name`
  - `spring.profiles.active` (e.g. `dev`)
- Secrets/credentials sử dụng `.env` + Docker secrets (nếu áp dụng).

Ví dụ biến môi trường hữu ích (trích):
```env
SPRING_PROFILES_ACTIVE=dev
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:5173

# Feign/Resilience4j (ví dụ)
FEIGN_CLIENT_CONFIG_DEFAULT_CONNECT_TIMEOUT=2000
FEIGN_CLIENT_CONFIG_DEFAULT_READ_TIMEOUT=5000
RESILIENCE4J_CIRCUITBREAKER_INSTANCES_DEFAULT_SLIDING_WINDOW_SIZE=20

# Kafka (Email-only)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
EMAIL_TOPIC_AUTH_REGISTERED=auth.registered
EMAIL_TOPIC_FORGOT_PASSWORD=auth.forgot-password.requested

# Data & Infra
REDIS_URI=redis://localhost:6379
POSTGRES_URL=jdbc:postgresql://localhost:5432/novaplay_user
MONGO_URI=mongodb://localhost:27017/novaplay_movie

# SMTP
SMTP_HOST=smtp.mailtrap.io
SMTP_PORT=2525
SMTP_USER=...
SMTP_PASS=...
```

---

## Security & Auth

- **JWT** (stateless). Dòng chảy điển hình:
  1. Đăng ký → `auth-service` tạo user (REST) → phát sự kiện email lên Kafka.
  2. Đăng nhập → Gateway forward đến `auth-service` để issue **access** & **refresh** token.
  3. Gateway validate JWT, gắn user context header tới downstream services (Feign).
- **Rate-limit** & **IP throttling** tại Gateway.
- **CORS**: cho phép FE `http://localhost:5173`.
- Tùy chọn **Keycloak** vẫn có thể tích hợp sau này (OIDC), nhưng hiện tại **Feign + JWT** là mặc định.

---

## Synchronous Communication (OpenFeign)

- **OpenFeign** dùng để gọi REST giữa các services (ví dụ: `movie-service` gọi `user-service` để lấy profile, `payment-service` gọi `user-service`/`promotion-service`…).
- **Best practices:**
  - `@EnableFeignClients` trên lớp cấu hình của service.
  - Dùng **serviceId** (Eureka) thay vì hard-coded URL để client-side load-balancing.
  - Thiết lập **timeouts**, **retries**, **circuit breakers** (Resilience4j) để tránh cascading failures.
  - Thống nhất **DTOs** (versioned) & **error contract** (RFC7807/problem+json khuyến nghị).
  - Idempotency cho các API có thể retry (dựa vào **Idempotency-Key** headers hoặc business keys).

---

## Asynchronous Messaging (Email only)

- **Scope giới hạn:** Kafka chỉ được dùng cho **email-service**.
- **Producers:** `auth-service` (đăng ký, quên mật khẩu), có thể thêm `user-service` khi cần.
- **Consumer:** `email-service`.
- **Topics ví dụ:**
  - `auth.registered` → gửi mail kích hoạt
  - `auth.forgot-password.requested` → gửi OTP/Link
- **Serialization:** `JsonSerializer`/`JsonDeserializer` với header type nhất quán (tránh mismatch package khi share DTO; hoặc dùng DTO contract chung trong module `utils`).

> Vì phần lớn nghiệp vụ là **synchronous** qua Feign, Kafka được giữ ở mức tối giản cho nhu cầu email để dễ debug và giảm độ phức tạp hệ thống.

---

## Databases & Storage

- **PostgreSQL**: `user-service`, có thể dùng cho `auth-service`.
- **MongoDB**: `movie-service` (metadata phim, cast, genre, indexing).
- **MySQL / PostgreSQL**: `payment-service`, `promotion-service` tùy chọn.
- **Redis**: cache, rate-limit, OTP, blacklisting tokens.
- **Object Storage**: MinIO/S3 cho poster, thumbnail, HLS segments.

Migrations: Flyway/Liquibase _[khuyến nghị]_ cho RDBMS; seeds tại thư mục `init-db`.

---

## Observability

- **Tracing**: OpenTelemetry Java agent → Grafana Tempo.
- **Metrics**: Micrometer → Prometheus → Grafana.
- **Logs**: JSON logs → Loki (tùy chọn).
- Health checks: `/actuator/health` trên mỗi service.

---

## API Documentation

- Mỗi service có **Springdoc OpenAPI**.
- Gateway aggregate Swagger UI → duyệt toàn bộ API tại một nơi.
- Quy ước version: `/api/v1/**`.

---

## Project Structure

```
NovaPlay/
├─ api-gateway/
├─ discovery-server/
├─ cloud-config/
├─ auth-service/
├─ user-service/
├─ movie-service/
├─ streaming-service/
├─ media-service/
├─ email-service/
├─ notification-service/
├─ payment-service/
├─ promotion-service/
├─ report-service/
├─ init-db/
├─ utils/
├─ docker-compose.yml
└─ README.md   ← you are here
```

---

## Testing

- **Unit tests:** JUnit5 + Mockito.
- **Integration tests:** Testcontainers (Postgres, Mongo, Kafka cho email-service).
---

## Deployment Notes

- Dev: Docker Compose (single node Kafka, chỉ email-service dùng).
- CI/CD: GitHub Actions (build, test, scan, publish images).

---

## Roadmap

- [ ] Hoàn thiện email flow qua Kafka (retry, DLQ, idempotency) — **Chỉ email-service**.
- [ ] Feign clients chuẩn hoá DTO + error contracts.
- [ ] Aggregated Swagger qua Gateway.
- [ ] OpenTelemetry (traces) → Tempo; dashboard Grafana.
- [ ] Payment flow sandbox + webhooks.
- [ ] Report-service + DWH pipeline.

---

## Contributing

Đóng góp theo GitFlow:
- Branches: `main`, `develop`, `feature/*`, `hotfix/*`
- Commit: Conventional Commits (`feat:`, `fix:`, `chore:`…)
- PR: yêu cầu code review, CI xanh.

---

## Maintainer

**Ngô Diệp Quang Huy** — Junior Java Developer (HCMC)  
GitHub: `@81quanghuy`  
Dự án cá nhân: NovaPlay

---

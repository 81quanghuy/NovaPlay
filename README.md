# NovaPlay — Microservices Movie Streaming Platform

> A production‑style, microservices‑based online movie platform built with Spring Boot and React. NovaPlay demonstrates API Gateway, service discovery, centralized configuration, event‑driven communication (Kafka), and modern DevOps practices (Docker, Observability).

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
- [Asynchronous Messaging](#asynchronous-messaging)
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
- Giao tiếp bất đồng bộ bằng Kafka (outbox, retry, DLQ)
- Bảo mật theo chuẩn JWT/OAuth2, rate‑limit, CORS
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
           ┌─────────────┼──────────────────────────────────────────────────┐
           │             │                                                  │
           ▼             ▼                                                  ▼
  ┌──────────────┐ ┌───────────────┐  ...  ┌────────────────┐     ┌─────────────────┐
  │ auth-service │ │ user-service  │       │ movie-service  │ ... │ streaming-serv. │
  └──────▲───────┘ └──────▲────────┘       └──────▲─────────┘     └────────▲────────┘
         │ JWT/OAuth2      │ users/profile          │ movies, genres            │ HLS/DASH
         │                 │                        │ search/filter             │ edge/Nginx
         │                 │                        │                           │
         │        ┌────────┴────────┐       ┌──────┴─────────┐          ┌──────┴───────┐
         │        │ email-service   │       │ notification-s.│          │ media-service │
         │        └────────▲────────┘       └──────▲─────────┘          └──────▲───────┘
         │                 │ Kafka topics           │ Kafka topics                │ object storage
         │                 │                        │                             │ (e.g., S3/MinIO)
         │                 │                        │
         ▼                 ▼                        ▼
   ┌────────────────────────────────────────────────────────────────────────────┐
   │                               Kafka Broker                                 │
   └────────────────────────────────────────────────────────────────────────────┘

   ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────────┐
   │ PostgreSQL (user)  │  │ MongoDB (movie)    │  │ MySQL (payment/promo)│
   └────────────────────┘  └────────────────────┘  └──────────────────────┘

   ┌──────────────────────────────────────┐
   │ Config Server (cloud-config)         │
   │ - centralized application.yml        │
   └──────────────────────────────────────┘
```

> **Note:** Kiến trúc có thể thay đổi theo tiến độ. Phần dưới mô tả “state” hiện tại và các mục đang triển khai.

---

## Services

| Service               | Port (dev) | Database          | Responsibilities |
|-----------------------|------------|-------------------|------------------|
| `api-gateway`         | `8072`     | —                 | Routing, CORS, rate‑limit, Swagger aggregation |
| `discovery-server`    | `8761`     | —                 | Eureka registry |
| `cloud-config`        | `8888`     | Git repo          | Centralized config |
| `auth-service`        | `8000`     | PostgreSQL/Redis* | Auth, JWT issuance, OTP/email flow* |
| `user-service`        | `8001`     | PostgreSQL        | User profile, preferences |
| `movie-service`       | `8002`     | MongoDB           | Movies, genres, casts, search |
| `streaming-service`   | `8003`     | — / ObjectStore   | HLS/DASH streaming, signed URLs |
| `media-service`       | `8004`     | ObjectStore       | Media ingestion, posters, thumbnails |
| `email-service`       | `8005`     | —                 | SMTP provider integration; Kafka consumer |
| `notification-service`| `8006`     | Redis/PostgreSQL  | Multichannel notifications; Kafka consumer |
| `payment-service`     | `8007`     | MySQL/PostgreSQL  | Payment sessions, webhooks |
| `promotion-service`   | `8008`     | MySQL/PostgreSQL  | Coupons, campaigns |
| `report-service`      | `8009`     | DWH (OLAP)        | Analytics, aggregates |
| `init-db` (folder)    | —          | —                 | DB schema/seed scripts |
| `utils` (folder)      | —          | —                 | Common libraries/utilities |

\* Redis dùng cho rate‑limit, captcha/OTP, token blacklist, hoặc cache.

---

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Cloud (Gateway, Config, Eureka), Spring Data (JPA/Mongo), Spring Security.
- **Async:** Apache Kafka (KRaft), Spring Cloud Stream (functional style), Outbox pattern, DLQ & retry/backoff.
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
cp .env.example .env   # chỉnh sửa thông số nếu cần
```

### 3) Build các service (tùy chọn nếu dùng JIT buildpacks)
```bash
mvn -T 1C -DskipTests clean package
```

### 4) Khởi chạy bằng Docker Compose
```bash
docker compose up -d
docker compose logs -f api-gateway
```
Mặc định các port phổ biến:
- Gateway: `http://localhost:8072`
- Discovery: `http://localhost:8761`
- Config: `http://localhost:8888`
- Keycloak/Auth (nếu dùng): `http://localhost:7080` hoặc `http://localhost:8000`
- Kafka: `localhost:9092` (PLAINTEXT)

### 5) Truy cập nhanh
- Swagger qua Gateway (aggregated): `http://localhost:8072/swagger-ui.html` (hoặc `/swagger-ui/index.html` tùy config)
- React FE (dev): `http://localhost:5173`

> **Troubleshooting nhanh**
> - **CORS**: kiểm tra CORS config ở **Gateway** (Origin FE `http://localhost:5173`).
> - **Kafka**: với Docker single‑broker (Bitnami), đảm bảo `KAFKA_CFG_ADVERTISED_LISTENERS` khớp `localhost:9092` cho client ngoài container.
> - **Email (SMTP)**: dùng provider thật (SendGrid/SES/Mailtrap) thay vì `localhost:25` trong môi trường dev.

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
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_URI=redis://localhost:6379
POSTGRES_URL=jdbc:postgresql://localhost:5432/novaplay_user
MONGO_URI=mongodb://localhost:27017/novaplay_movie
SMTP_HOST=smtp.mailtrap.io
SMTP_PORT=2525
SMTP_USER=...
SMTP_PASS=...
```

---

## Security & Auth

- Hỗ trợ **JWT** (stateless). Dòng chảy điển hình:
  1. Đăng ký → xác minh email (Kafka → email‑service).
  2. Đăng nhập → Gateway forward đến `auth-service` để issue **access** & **refresh** token.
  3. Gateway validate JWT, gắn user context header tới downstream services.
- **Rate‑limit** & **IP throttling** tại Gateway.
- **CORS**: cho phép FE `http://localhost:5173`.
- Tùy chọn **Keycloak** (OAuth2/OIDC) thay thế `auth-service` nếu cần. Đã có cấu hình mẫu port `7080`.

---

## Asynchronous Messaging

- **Broker:** Apache Kafka.
- **Spring Cloud Stream (functional):** producers/consumers đơn giản, hỗ trợ retry/backoff.
- **Outbox pattern:** tránh mất sự kiện, đảm bảo “at‑least‑once” + idempotency ở consumer.
- **DLQ:** topic `*.DLT` cho message lỗi vĩnh viễn.
- Chủ đề ví dụ:
  - `auth.registered` → `email-service` gửi mail kích hoạt
  - `auth.forgot-password.requested` → `email-service` gửi OTP/Link
  - `notification.send` → đa kênh (email, in‑app)

> **Serialization tip:** Dùng `JsonSerde` với `trusted.packages=*` hoặc ánh xạ type header rõ ràng giữa services để tránh lỗi deserializer cross‑package.

---

## Databases & Storage

- **PostgreSQL**: `user-service`, có thể dùng cho `auth-service`.
- **MongoDB**: `movie-service` (metadata phim, cast, genre, indexing).
- **MySQL / PostgreSQL**: `payment-service`, `promotion-service` tùy chọn.
- **Redis**: cache, rate‑limit, OTP, blacklisting tokens.
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
- **Integration tests:** Testcontainers (Kafka, Postgres, Mongo).
- **Contract tests** (khuyến nghị): Spring Cloud Contract giữa services.
- **Load test** (khuyến nghị): k6/Gatling; chú ý rate‑limit & idempotency.

---

## Deployment Notes

- Dev: Docker Compose (single node Kafka).
- Prod: khuyến nghị Kubernetes (Helm charts); externalized secrets; managed Kafka (Confluent/Redpanda).
- CI/CD: GitHub Actions (build, test, scan, publish images).

---

## Roadmap

- [ ] Hoàn thiện email/notification flow qua Kafka (retry, DLQ, idempotency).
- [ ] Streaming HLS ổn định (Nginx/segmenter, signed URL).
- [ ] Search nâng cao cho movie (text index, suggestions).
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

## License

MIT — tự do sử dụng cho học tập và thương mại (giữ credit). Tham khảo file `LICENSE` nếu có thay đổi.

---

## Maintainer

**Ngô Diệp Quang Huy** — Junior Java Developer (HCMC)  
GitHub: `@81quanghuy`  
Dự án học tập & portfolio: NovaPlay

---

> 💡 Nếu bạn dùng README này cho repo khác, nhớ chỉnh lại tên, ports, và danh sách services.

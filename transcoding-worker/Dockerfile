# Image production cho transcoding-worker.
#
# Build từ thư mục gốc của repo vì service vẫn là module con của pom cha:
#   docker build -f transcoding-worker/Dockerfile -t novaplay/transcoding-worker:v0.0.1 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn một module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .

RUN mvn -B -q clean package -pl transcoding-worker -am -DskipTests

# ---- Giai đoạn chạy ----
# Khác duy nhất so với Dockerfile của các service khác trong repo: cần cài thêm gói OS ffmpeg —
# đây là lý do ffmpeg-worker không dùng jib (jib không cài được gói OS lên base distroless).
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl ffmpeg

# Không chạy bằng root.
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

ARG OTEL_AGENT_VERSION=2.11.0
RUN mkdir -p /app/libs && \
    curl -fsSL -o /app/libs/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

COPY --from=build --chown=novaplay:novaplay /build/transcoding-worker/target/transcoding-worker-v0.0.1.jar app.jar

# Scratch space cho video thô + segment HLS trong lúc xử lý — không dùng /tmp mặc định (quá nhỏ
# cho file cỡ GB), và filesystem gốc là read-only trong k8s (xem k8s/transcoding-worker/deployment.yaml).
RUN mkdir -p /scratch && chown novaplay:novaplay /scratch

USER novaplay
EXPOSE 8400

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8400/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

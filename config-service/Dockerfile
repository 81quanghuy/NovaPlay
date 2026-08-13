# Image production cho config-service.
#
# Build từ thư mục gốc của repo vì service vẫn là module con của pom cha:
#   docker build -f config-service/Dockerfile -t novaplay/config-service:v0.0.1 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn một module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .

RUN mvn -B -q clean package -pl config-service -am -DskipTests

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

ARG OTEL_AGENT_VERSION=2.11.0
RUN mkdir -p /app/libs && \
    curl -fsSL -o /app/libs/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

COPY --from=build --chown=novaplay:novaplay /build/config-service/target/config-service-v0.0.1.jar app.jar

USER novaplay
EXPOSE 8500

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8500/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

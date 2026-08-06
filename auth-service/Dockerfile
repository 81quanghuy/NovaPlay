# Image production cho auth-service.
#
# Build từ thư mục gốc của repo để composite build còn thấy được module utils:
#   docker build -f auth-service/Dockerfile -t novaplay/authservice:v0.0.1 .

# ---- Giai đoạn build ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# settings.gradle của auth-service dùng includeBuild('../utils') nên cần copy cả repo
# (.dockerignore đã loại target/, build/ và các file rác khác).
COPY . .
WORKDIR /build/auth-service
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q
RUN ./gradlew bootJar -x test --no-daemon -q

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# Agent phải nằm thành file riêng và được truyền qua -javaagent; khai báo nó như một dependency
# Gradle chỉ nhét jar vào fat jar chứ không kích hoạt được instrumentation.
# Manifest Kubernetes trỏ JAVA_TOOL_OPTIONS tới đúng đường dẫn này.
ARG OTEL_AGENT_VERSION=2.18.1
RUN mkdir -p /app/libs && \
    curl -fsSL -o /app/libs/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

# Gradle Spring Boot plugin đặt jar tại build/libs/<rootProject.name>-<version>.jar
# rootProject.name = 'authservice', version = '0.0.1' → authservice-0.0.1.jar
COPY --from=build --chown=novaplay:novaplay /build/auth-service/build/libs/authservice-0.0.1.jar app.jar

USER novaplay
EXPOSE 8000

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8000/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# Image production cho api-gateway.
#
# Build từ thư mục api-gateway:
#   docker build -t novaplay/apigateway:v1.0.0 .

# ---- Giai đoạn build ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# Copy wrapper + build scripts trước để cache layer download dependencies
COPY gradlew gradlew.bat gradle.properties settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src ./src
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
# rootProject.name = 'apigateway', version = '1.0.0' → apigateway-1.0.0.jar
COPY --from=build --chown=novaplay:novaplay /build/build/libs/apigateway-1.0.0.jar app.jar

USER novaplay
EXPOSE 8072

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8072/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

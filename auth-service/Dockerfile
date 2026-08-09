# Image production cho auth-service.
#
# Service đứng độc lập, build context là chính thư mục auth-service:
#   docker build -t novaplay/authservice:v0.0.1 auth-service/

# ---- Giai đoạn build ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

COPY gradlew ./gradlew
COPY gradlew.bat ./gradlew.bat
COPY gradle.properties ./gradle.properties
COPY settings.gradle ./settings.gradle
COPY build.gradle ./build.gradle
COPY gradle ./gradle

COPY src ./src

# Build auth-service
WORKDIR /build

RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon -q

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
COPY --from=build --chown=novaplay:novaplay /build/build/libs/authservice-0.0.1.jar app.jar
USER novaplay
EXPOSE 8000

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8000/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

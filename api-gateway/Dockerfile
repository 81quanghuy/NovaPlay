# Image production cho api-gateway.
#
# Build từ thư mục gốc của repo để có thể truy cập cả module utils:
#   docker build -f api-gateway/Dockerfile -t novaplay/apigateway:v1.0.0 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ 14 module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn hai module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .
RUN mvn -B -q clean package -pl api-gateway -am -DskipTests

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# Agent phải nằm thành file riêng và được truyền qua -javaagent; khai báo nó như một dependency
# Maven chỉ nhét jar vào fat jar chứ không kích hoạt được instrumentation.
# Manifest Kubernetes trỏ JAVA_TOOL_OPTIONS tới đúng đường dẫn này.
ARG OTEL_AGENT_VERSION=2.11.0
RUN mkdir -p /app/libs && \
    curl -fsSL -o /app/libs/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

# finalName ở pom gốc là ${project.artifactId}-v${project.version}; artifactId là
# "apigateway" (không dấu gạch) chứ không phải "api-gateway".
COPY --from=build --chown=novaplay:novaplay /build/api-gateway/target/apigateway-v1.0.0.jar app.jar

USER novaplay
# Gateway thật sự chạy ở 8072 (khớp CLAUDE.md); Dockerfile cũ khai báo nhầm 9000.
EXPOSE 8072

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8072/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

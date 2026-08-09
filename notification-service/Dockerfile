# Image production cho notification-service.
#
# Build từ thư mục gốc của repo vì service vẫn là module con của pom cha:
#   docker build -f notification-service/Dockerfile -t novaplay/notification-service:v0.0.1 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ chọn
# hai module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .

RUN mvn -B -q clean package -pl notification-service -am -DskipTests

# ---- Giai đoạn chạy ----
# eclipse-temurin thay cho openjdk (kho openjdk đã ngừng bảo trì); dùng JRE thay vì JDK đầy đủ.
FROM eclipse-temurin:21-jre-alpine

# curl phục vụ HEALTHCHECK bên dưới cũng như healthcheck của docker-compose.
RUN apk add --no-cache curl

# Không chạy bằng root.
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# Agent phải nằm thành file riêng và được truyền qua -javaagent; khai báo nó như một dependency
# Maven chỉ nhét jar vào fat jar chứ không kích hoạt được instrumentation.
#
# Tải về dưới CẢ HAI tên: manifest Kubernetes dùng opentelemetry-javaagent.jar, còn
# docker-compose/qa/common-config.yml lại trỏ tới opentelemetry-javaagent-2.11.0.jar. Chừng nào
# hai nơi đó chưa thống nhất, thiếu một trong hai tên nghĩa là agent không nạp và service mất
# hoàn toàn trace mà không có lỗi nào báo ra.
ARG OTEL_AGENT_VERSION=2.11.0
RUN mkdir -p /app/libs && \
    curl -fsSL -o /app/libs/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" && \
    ln -s /app/libs/opentelemetry-javaagent.jar /app/libs/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar

# finalName ở pom gốc là ${project.artifactId}-v${project.version}; ở module này artifactId
# đúng bằng "notification-service" nên tên jar là notification-service-v0.0.1.jar.
COPY --from=build --chown=novaplay:novaplay /build/notification-service/target/notification-service-v0.0.1.jar app.jar

USER novaplay
EXPOSE 8900

# MaxRAMPercentage để JVM tôn trọng giới hạn bộ nhớ của container thay vì bộ nhớ máy chủ.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8900/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# Image production cho discovery-server.
#
# Build từ thư mục gốc của repo để có thể truy cập cả module utils:
#   docker build -f discovery-server/Dockerfile -t novaplay/discoveryserver:v0.0.1 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ 14 module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn hai module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .
RUN mvn -B -q clean package -pl discovery-server -am -DskipTests

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# finalName ở pom gốc là ${project.artifactId}-v${project.version}; artifactId là
# "discoveryserver" (không dấu gạch) chứ không phải "discovery-server".
COPY --from=build --chown=novaplay:novaplay /build/discovery-server/target/discoveryserver-v0.0.1.jar app.jar

USER novaplay
EXPOSE 8761

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8761/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

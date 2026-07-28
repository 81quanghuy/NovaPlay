# Image production cho cloud-config.
#
# Build từ thư mục gốc của repo để có thể truy cập cả module utils:
#   docker build -f cloud-config/Dockerfile -t novaplay/cloudconfig:v1.0.0 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ 14 module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn hai module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .
RUN mvn -B -q clean package -pl cloud-config -am -DskipTests

# ---- Giai đoạn chạy ----
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# finalName ở pom gốc là ${project.artifactId}-v${project.version}; artifactId là
# "cloudconfig" (không dấu gạch) chứ không phải "cloud-config".
COPY --from=build --chown=novaplay:novaplay /build/cloud-config/target/cloudconfig-v1.0.0.jar app.jar

USER novaplay
EXPOSE 8888

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8888/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

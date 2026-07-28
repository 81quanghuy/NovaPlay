# Image production cho user-service.
#
# Build từ thư mục gốc của repo để có thể truy cập cả module utils:
#   docker build -f user-service/Dockerfile -t novaplay/userservice:v0.0.1 .

# ---- Giai đoạn build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pom gốc liệt kê toàn bộ 14 module nên Maven đòi tất cả phải hiện diện, kể cả khi -pl chỉ
# chọn hai module. Copy cả cây thư mục (.dockerignore đã loại target/ và các file rác khác).
COPY . .

# -am kéo theo module utils mà user-service phụ thuộc.
RUN mvn -B -q clean package -pl user-service -am -DskipTests

# ---- Giai đoạn chạy ----
# eclipse-temurin thay cho openjdk (kho openjdk đã ngừng bảo trì); dùng JRE thay vì JDK đầy đủ.
FROM eclipse-temurin:21-jre-alpine

# curl phục vụ HEALTHCHECK bên dưới cũng như healthcheck của docker-compose.
RUN apk add --no-cache curl

# Không chạy bằng root.
RUN addgroup -S novaplay && adduser -S -G novaplay novaplay
WORKDIR /app

# finalName ở pom gốc là ${project.artifactId}-v${project.version}, mà artifactId là
# "userservice" chứ không phải "user-service".
COPY --from=build --chown=novaplay:novaplay /build/user-service/target/userservice-v0.0.1.jar app.jar

USER novaplay
EXPOSE 8700

# MaxRAMPercentage để JVM tôn trọng giới hạn bộ nhớ của container thay vì bộ nhớ máy chủ.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8700/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

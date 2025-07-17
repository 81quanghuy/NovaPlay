# Dockerfile for Cloud Config Service
FROM openjdk:21-jdk-slim

LABEL maintainer="81quanghuy"

# --- THAY ĐỔI ---
# Cài đặt thêm 'git' bên cạnh 'curl'
RUN apt-get update && apt-get install -y curl git --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/cloud-config-v0.0.1.jar /app.jar

# Sao chép và cấp quyền thực thi cho entrypoint script
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

# Sử dụng script làm ENTRYPOINT
ENTRYPOINT ["./entrypoint.sh"]

EXPOSE 8888

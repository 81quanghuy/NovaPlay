# Dockerfile for Cloud Config Service
FROM openjdk:21-jdk-slim

LABEL maintainer="81quanghuy"

# Cài đặt thêm 'git' bên cạnh 'curl'
#RUN apt-get update && apt-get install -y curl git --no-install-recommends && \
#    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/cloud-config-v0.0.1.jar /app.jar
#entrypoint to run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

EXPOSE 8888

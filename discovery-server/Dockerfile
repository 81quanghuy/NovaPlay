# Dockerfile for Discovery Server
FROM openjdk:21-jdk-slim

# Cập nhật danh sách package và cài đặt curl mà không lưu cache để giữ image nhẹ
RUN apt-get update && apt-get install -y curl --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Copy the JAR file into the container
COPY target/discovery-server-v0.0.1.jar /app.jar

#entrypoint to run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

# Expose the port the app runs on
EXPOSE 8761
# Dockerfile for Cloud Config Service
FROM openjdk:21-jdk-slim

# Set maintainer label
LABEL maintainer="81quanghuy"

# Set the working directory
WORKDIR /app

# Copy the JAR file into the container
COPY target/cloud-config-v0.0.1.jar /app.jar

#entrypoint to run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

# Expose the port the app runs on
EXPOSE 8888
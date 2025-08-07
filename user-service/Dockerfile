# Dockerfile for User service
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file into the container with a generic name
COPY target/user-service-v0.0.1.jar /app/app.jar
COPY entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh
#entrypoint to run the application
ENTRYPOINT ["/entrypoint.sh"]

# Expose the port the app runs on
EXPOSE 8700
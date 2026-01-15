# ==============================================
# Multi-stage Dockerfile for Spring Boot Services
# Usage: docker build --build-arg SERVICE_PATH=apps/waiting-service -t waiting-service .
# ==============================================

# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Gradle wrapper & settings
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

# Copy all modules (for dependency resolution)
COPY libs libs
COPY apps apps
COPY infrastructure infrastructure

# Build argument for service path (e.g., apps/waiting-service)
ARG SERVICE_PATH

# Build the specific service
RUN chmod +x ./gradlew && \
    ./gradlew :${SERVICE_PATH}:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Build argument for service path
ARG SERVICE_PATH
ARG SERVICE_NAME

# Copy the built JAR
COPY --from=builder /app/${SERVICE_PATH}/build/libs/*.jar app.jar

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

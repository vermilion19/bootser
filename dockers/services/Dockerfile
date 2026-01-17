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
COPY settings-docker.gradle settings.gradle
COPY build.gradle .

# Copy all modules (for dependency resolution)
COPY libs libs
COPY apps apps
COPY infrastructure infrastructure

# Build argument for service path (e.g., apps/waiting-service)
ARG SERVICE_PATH

#COPY ${SERVICE_PATH} ${SERVICE_PATH}

#RUN echo "rootProject.name = 'booster'" > settings.gradle && \
#    # (1) libs 폴더 안에 있는 build.gradle을 찾아서 include 추가
#    find libs -name build.gradle | sed 's|/build.gradle||;s|/|:|g' | awk '{print "include \x27" $0 "\x27"}' >> settings.gradle && \
#    # (2) 현재 서비스 폴더(SERVICE_PATH)를 include 추가
#    echo "include '${SERVICE_PATH}'" | sed 's|/|:|g' >> settings.gradle

# Build the specific service
# [수정됨] SERVICE_PATH 대신 치환된 GRADLE_PROJECT_PATH를 사용해야 합니다.
RUN chmod +x ./gradlew && \
    GRADLE_PROJECT_PATH=$(echo "${SERVICE_PATH}" | sed 's/\//:/g') && \
    ./gradlew :${GRADLE_PROJECT_PATH}:bootJar -x test --no-daemon

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
# [주의] 파일 경로는 슬래시(/)가 맞으므로 여기는 SERVICE_PATH를 그대로 쓰는 게 맞습니다.
COPY --from=builder /app/${SERVICE_PATH}/build/libs/*.jar app.jar

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
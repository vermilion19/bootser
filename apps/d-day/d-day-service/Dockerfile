# Stage 1: Build (프로젝트 루트에서 Gradle 빌드)
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Gradle Wrapper 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/

# 의존 모듈 소스 복사
COPY libs/ libs/
COPY apps/d-day/ apps/d-day/

# 빌드 (테스트 제외)
RUN chmod +x gradlew && ./gradlew :apps:d-day:d-day-service:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /workspace/apps/d-day/d-day-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

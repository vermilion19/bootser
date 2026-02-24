# Stage 1: Build (프로젝트 루트에서 Gradle 빌드)
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/

COPY libs/ libs/
COPY infrastructure/gateway-service/ infrastructure/gateway-service/

RUN chmod +x gradlew && ./gradlew :infrastructure:gateway-service:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /workspace/infrastructure/gateway-service/build/libs/*.jar app.jar

EXPOSE 6000

ENTRYPOINT ["java", "-jar", "app.jar"]

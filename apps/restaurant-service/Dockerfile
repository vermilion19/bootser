# Stage 1: Build (프로젝트 루트에서 Gradle 빌드)
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY libs/ libs/
COPY apps/restaurant-service/ apps/restaurant-service/

# 다른 앱 모듈은 Gradle 프로젝트 구성에만 필요 (build.gradle만 복사)
COPY apps/waiting-service/build.gradle apps/waiting-service/build.gradle
COPY apps/auth-service/build.gradle apps/auth-service/build.gradle
COPY apps/notification-service/build.gradle apps/notification-service/build.gradle
COPY apps/promotion-service/build.gradle apps/promotion-service/build.gradle
COPY apps/d-day/build.gradle apps/d-day/build.gradle
COPY apps/d-day/d-day-service/build.gradle apps/d-day/d-day-service/build.gradle
COPY apps/kotlin/build.gradle apps/kotlin/build.gradle
COPY apps/kotlin/chatting-service/build.gradle apps/kotlin/chatting-service/build.gradle
COPY apps/kotlin/damagochi-service/build.gradle apps/kotlin/damagochi-service/build.gradle
COPY apps/kotlin/board-service/build.gradle apps/kotlin/board-service/build.gradle
COPY apps/kotlin/shopping-service/build.gradle apps/kotlin/shopping-service/build.gradle
COPY infrastructure/discovery-service/build.gradle infrastructure/discovery-service/build.gradle
COPY infrastructure/gateway-service/build.gradle infrastructure/gateway-service/build.gradle
COPY playground/java-coding-test/build.gradle playground/java-coding-test/build.gradle
COPY playground/kotlin-lab/build.gradle playground/kotlin-lab/build.gradle
COPY agents/TestMakerAgent/build.gradle agents/TestMakerAgent/build.gradle

RUN chmod +x gradlew && ./gradlew :apps:restaurant-service:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /workspace/apps/restaurant-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
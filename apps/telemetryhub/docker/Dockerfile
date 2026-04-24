FROM gradle:9.2.1-jdk21-alpine AS builder

WORKDIR /workspace
COPY . .

ARG MODULE_TASK
ARG MODULE_DIR

RUN chmod +x gradlew && ./gradlew ${MODULE_TASK}:bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

ARG MODULE_DIR
COPY --from=builder /workspace/${MODULE_DIR}/build/libs/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

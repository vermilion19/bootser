FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace
COPY . .

ARG MODULE_TASK
ARG MODULE_DIR

RUN chmod +x gradlew && ./gradlew ${MODULE_TASK}:bootJar --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

ARG MODULE_DIR
COPY --from=builder /workspace/${MODULE_DIR}/build/libs/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:25-jdk AS builder

ARG SERVICE_MODULE

WORKDIR /workspace

COPY . .

RUN mkdir -p playground/java-coding-test playground/kotlin-lab

RUN chmod +x gradlew && ./gradlew :apps:query-burst-msa:${SERVICE_MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre

ARG SERVICE_MODULE

WORKDIR /app

COPY --from=builder /workspace/apps/query-burst-msa/${SERVICE_MODULE}/build/libs/*.jar app.jar

EXPOSE 18111 18112 18113 18114 18115

ENTRYPOINT ["java", "-jar", "app.jar"]

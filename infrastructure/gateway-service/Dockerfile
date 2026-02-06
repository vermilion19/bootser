FROM eclipse-temurin:25-jre

WORKDIR /app

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 6000

ENTRYPOINT ["java", "-jar", "app.jar"]

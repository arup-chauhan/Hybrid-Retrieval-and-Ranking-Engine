FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/orchestration-service.jar orchestration-service.jar
ENTRYPOINT ["java", "-jar", "orchestration-service.jar"]

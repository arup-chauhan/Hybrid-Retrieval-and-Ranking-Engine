FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/vector-service-1.0.0.jar vector-service.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "vector-service.jar"]

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/ingestion-service-1.0.0.jar ingestion-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "ingestion-service.jar"]

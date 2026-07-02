FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY backend/target/toolbox-1.0.0.jar app.jar

EXPOSE 8899

ENTRYPOINT ["java", "-jar", "app.jar"]

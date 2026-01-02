FROM eclipse-temurin:21-jdk

RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

USER spring:spring

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
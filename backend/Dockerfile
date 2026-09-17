# ---- build stage: compile the Spring Boot jar with JDK 21 + Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# cache dependencies first (pom rarely changes -> faster rebuilds)
COPY pom.xml .
RUN mvn -B dependency:go-offline || true
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- run stage: small JRE-only image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/orar-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

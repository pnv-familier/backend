# ---- Build stage ----
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

ENTRYPOINT ["java", "-Xmx256m", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT java -Xmx300m -Xss512k -XX:+UseSerialGC -Dserver.port=${PORT} -jar app.jar
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/application-log-parser-1.0.0.jar /app/application-log-parser.jar

EXPOSE 8080
EXPOSE 9090

CMD ["java", "-jar", "/app/application-log-parser.jar"]

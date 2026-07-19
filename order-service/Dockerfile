FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY order-service/pom.xml ./pom.xml
COPY order-service/src ./src
COPY orchestrator-service/src/main/java/com/example/saga/orchestrator/dto/ ./src/main/java/com/example/saga/orchestrator/dto/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY orchestrator-service/pom.xml ./pom.xml
COPY orchestrator-service/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
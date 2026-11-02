FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace/backend-java
COPY backend-java/pom.xml ./pom.xml
COPY backend-java/src ./src
COPY frontend ../frontend

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build --chown=spring:spring \
    /workspace/backend-java/target/inventorio-1.0.0.jar /app/inventorio.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/inventorio.jar"]

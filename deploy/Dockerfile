FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY patients-core/pom.xml patients-core/pom.xml
COPY patients-adapters/pom.xml patients-adapters/pom.xml
COPY patients-app/pom.xml patients-app/pom.xml
RUN mvn -B dependency:go-offline
COPY patients-core patients-core
COPY patients-adapters patients-adapters
COPY patients-app patients-app
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system patients \
    && useradd --system --gid patients --home-dir /app --no-create-home patients
COPY --from=build --chown=patients:patients /workspace/patients-app/target/patients-app-0.1.0-SNAPSHOT.jar app.jar
USER patients
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---- Build stage: compile and package with Maven on Java 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached when only the sources change.
COPY pom.xml .
RUN mvn -B -Dmaven.test.skip=true dependency:resolve

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

# ---- Runtime stage: a slim Java 21 JRE, running as a non-root user ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create an unprivileged user and a writable uploads directory for notice attachments / ID photos.
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app

# The app reads the PORT variable (application-prod.yml: server.port: ${PORT:...}).
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

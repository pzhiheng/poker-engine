# ─── Stage 1: build ───────────────────────────────────────────────────────────
# Full JDK + Maven wrapper to compile and package the fat-jar.
# This layer is cached as long as pom.xml + .mvn/ don't change.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Resolve dependencies before copying source so Docker cache is reused on
#    source-only changes.
COPY .mvn   .mvn
COPY mvnw   .
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# 2. Copy source and build.  Tests are skipped here — they run in CI via
#    ./mvnw verify against a real Postgres service (see .github/workflows/ci.yml).
COPY src src
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: runtime ─────────────────────────────────────────────────────────
# Slim JRE-only image — no compiler, no Maven cache, much smaller footprint.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Drop root privileges: create a dedicated user and run as it.
RUN addgroup -S poker && adduser -S poker -G poker
USER poker

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

# Spring Boot's executable-jar launcher; JVM flags can be passed via
# the JAVA_TOOL_OPTIONS env var (e.g. -Xmx512m) without changing this file.
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── Stage 1: dependency resolution ────────────────────────────────────────────
# Cache Maven deps, plugin binaries, and generated proto stubs in a separate
# layer so source-only changes don't re-download everything.
FROM eclipse-temurin:21-jdk-alpine AS deps

# gcompat provides glibc compatibility shims on Alpine (musl libc).
# Required because protoc-gen-grpc-java native binaries for linux/arm64
# are glibc-linked; without gcompat they cannot execute on musl Alpine
# (affects Apple Silicon + linux/aarch64 Docker builds).
RUN apk add --no-cache gcompat

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download compile/test deps and the protoc + protoc-gen-grpc-java binaries,
# then generate Java stubs from src/main/proto/odds.proto.
# Layer is cached as long as pom.xml does not change.
RUN ./mvnw -B --no-transfer-progress \
        dependency:resolve \
        dependency:resolve-plugins \
        -q

# ── Stage 2: compile + package ────────────────────────────────────────────────
FROM deps AS builder

# Copy source code; invalidates this layer only on src/ changes.
COPY src/ src/

# Tests are skipped here — they require a live Postgres and run in CI
# via ./mvnw verify with a service container (see .github/workflows/ci.yml).
RUN ./mvnw -B --no-transfer-progress package -DskipTests -q

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S poker && adduser -S poker -G poker

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

USER poker

# UseContainerSupport  — honour the cgroup memory limit (set by Docker)
# MaxRAMPercentage=75  — use up to 75 % of the container's RAM for the heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

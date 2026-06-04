# ── Stage 1: dependency resolution ────────────────────────────────────────────
# Use the Debian-based JDK (not Alpine) so that protoc-gen-grpc-java native
# binaries — which are glibc + libstdc++ linked — can execute on all
# architectures including ARM64 (Apple Silicon).  Alpine's musl libc +
# gcompat shim is not sufficient for C++ binaries that use std::system_error.
FROM eclipse-temurin:21-jdk AS deps

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
FROM eclipse-temurin:21-jre AS runtime

RUN groupadd -r poker && useradd -r -g poker poker

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

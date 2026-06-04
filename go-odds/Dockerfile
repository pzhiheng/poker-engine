# ── Stage 1: build ────────────────────────────────────────────────────────────
# Use Amazon ECR Public as the pull source.  It mirrors Docker Hub official
# images without the anonymous rate limits that cause CI timeouts.
# Local alternative: docker build --build-arg BASE=golang:1.26-alpine .
ARG BASE=public.ecr.aws/docker/library/golang:1.26-alpine
FROM ${BASE} AS builder

# Install certificates so gRPC TLS works (not needed with plaintext, but
# included so the image is production-ready without modification).
RUN apk add --no-cache ca-certificates

WORKDIR /build

# Download dependencies first — this layer is cached as long as go.mod/go.sum
# don't change, which speeds up subsequent builds considerably.
COPY go.mod go.sum ./
RUN go mod download

# Copy the rest of the source and compile.
# -trimpath  removes local machine paths from stack traces
# -ldflags="-s -w" strips debug symbols → ~30 % smaller binary
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build \
        -trimpath \
        -ldflags="-s -w" \
        -o /go-odds \
        ./cmd/server

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
# Alpine (musl) works fine because the binary is CGO_ENABLED=0 / fully static.
# Alpine includes busybox wget, which is required by the Docker Compose
# healthcheck (distroless/static has no shell or wget so CMD-SHELL fails).
FROM public.ecr.aws/docker/library/alpine:3.21 AS runtime

RUN addgroup -S gogrp && adduser -S gouser -G gogrp

WORKDIR /

# Copy the statically linked binary from the build stage.
COPY --from=builder /go-odds /go-odds

# HTTP probe port  : 8081
# gRPC service port: 50051
EXPOSE 8081 50051

USER gouser:gogrp

ENTRYPOINT ["/go-odds"]

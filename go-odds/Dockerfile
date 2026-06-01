# ── Stage 1: build ────────────────────────────────────────────────────────────
# Use the same minor version as go.mod to avoid toolchain surprises.
FROM golang:1.26-alpine AS builder

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
# distroless/static has no shell, no package manager — minimal attack surface.
FROM gcr.io/distroless/static:nonroot AS runtime

WORKDIR /

# Copy the statically linked binary from the build stage.
COPY --from=builder /go-odds /go-odds

# HTTP probe port  : 8081
# gRPC service port: 50051
EXPOSE 8081 50051

# Run as non-root user (uid 65532, provided by distroless/static:nonroot).
USER nonroot:nonroot

ENTRYPOINT ["/go-odds"]

# poker-engine

> Real-time single-table No-Limit Texas Hold'em — Java + Go microservice showcase

[![CI](https://github.com/YOUR_USERNAME/poker-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/poker-engine/actions)

A **server-authoritative**, **play-money** poker platform built to demonstrate
production-quality Java and Go engineering:

| Service      | Stack | Responsibility |
|--------------|-------|----------------|
| `java-api`   | Java 21, Spring Boot 3.5, Spring Security, WebSocket/STOMP, JPA | Auth, table management, real-time broadcast, hand state machine |
| `go-odds`    | Go 1.22+, gRPC, Prometheus | Hand equity (exact + Monte Carlo), pprof profiling |

---

## Quick start

```bash
# 1. Clone
git clone https://github.com/YOUR_USERNAME/poker-engine.git
cd poker-engine

# 2. Boot everything (Postgres, java-api, go-odds, Prometheus, Grafana, Jaeger)
docker compose up --build

# 3. Open
#   API docs  → http://localhost:8080/swagger-ui.html
#   Metrics   → http://localhost:9092  (Prometheus)
#   Dashboards→ http://localhost:3000  (Grafana, admin / poker)
#   Traces    → http://localhost:16686 (Jaeger)
```

---

## Architecture

```
Browser A ──┐                              ┌── Prometheus
Browser B ──┤── REST / WebSocket/STOMP ──► java-api ──gRPC──► go-odds
            │                              │     └── OpenTelemetry ──► Jaeger
            │                              └── PostgreSQL
```

**java-api** is the single source of truth for table state.
Every action is validated server-side, written to `hand_events` transactionally,
then broadcast over STOMP to all connected clients.

**go-odds** provides equity calculations at flop/turn/river via gRPC.
It runs Monte Carlo in a goroutine worker pool, supports context cancellation,
and exposes `/metrics` (Prometheus) and `/debug/pprof`.

---

## API surface

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Create account |
| POST | `/auth/login` | Obtain JWT |
| POST | `/tables` | Create table |
| POST | `/tables/{id}/join` | Join a seat |
| POST | `/tables/{id}/ready` | Mark ready |
| POST | `/tables/{id}/actions` | Submit fold/check/call/bet/raise/all-in |
| GET  | `/tables/{id}` | Current table state |
| GET  | `/hands/{id}/replay` | Frame-by-frame replay |
| WS   | `/ws` (STOMP) | Real-time push |
| STOMP topic | `/topic/tables.{id}` | Table events broadcast |
| gRPC | `OddsService.CalculateEquity` | Called internally by java-api |

Full OpenAPI spec auto-generated at `/swagger-ui.html` when running.

---

## Project structure

```
poker-engine/
├── java-api/           Spring Boot service
│   └── src/main/java/com/poker/
│       ├── domain/     models, services, repositories
│       ├── api/        REST controllers, WebSocket handlers, DTOs
│       └── config/     Security, WebSocket, gRPC client, OpenTelemetry
├── go-odds/            Go gRPC + HTTP service
│   ├── cmd/server/     main entry point
│   ├── internal/
│   │   ├── evaluator/  7-card hand evaluator
│   │   ├── montecarlo/ concurrent worker pool
│   │   └── odds/       business logic
│   └── pkg/grpcserver/ gRPC server implementation
├── proto/              odds.proto (shared IDL)
├── docs/               prometheus.yml, architecture diagrams
├── docker-compose.yml
├── ASSUMPTIONS.md      ← scope freeze; read before coding
└── .github/workflows/  ci.yml
```

---

## Testing

```bash
# Java
cd java-api && ./mvnw test          # unit tests
./mvnw verify                        # + Testcontainers integration tests

# Go
cd go-odds && go test ./...          # all packages
go test -race ./...                  # with race detector
go test -bench=. ./internal/...      # benchmarks
```

---

## Milestones

- [ ] **Week 1** — Auth, table REST, DB schema
- [ ] **Week 2** — WebSocket + poker core (deal → showdown)
- [ ] **Week 3** — go-odds gRPC + observability
- [ ] **Week 4** — Demo polish, CI, recording

See [ASSUMPTIONS.md](ASSUMPTIONS.md) for full scope definition and out-of-scope list.

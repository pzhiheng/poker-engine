# poker-engine — Scope & Assumptions

> **Day 1 artifact.** Read this before touching any code.
> Every decision below is deliberate. Violating these bounds is scope creep.

---

## What this project IS

A **server-authoritative, single-table, play-money** No-Limit Texas Hold'em platform
built to demonstrate Java + Go production engineering, not to run a real poker site.

| Service     | Language | Responsibility |
|-------------|----------|----------------|
| `java-api`  | Java 21 / Spring Boot | REST API, JWT auth, WebSocket/STOMP broadcast, table state machine, persistence |
| `go-odds`   | Go 1.22+             | Hand equity (exact + Monte Carlo), gRPC interface, Prometheus metrics |

---

## Hard scope (IN)

- Single table, **2–6 players**, play-money chips only
- Standard NLHE: blinds, deal, preflop → flop → turn → river → showdown
- Actions: `fold`, `check`, `call`, `bet`, `raise`, `all-in`
- Server validates every action; clients only send intent
- Action event log + hand snapshot → replay API
- JWT-based auth (register, login); no OAuth, no social login
- WebSocket/STOMP push for table state & action log
- gRPC: `java-api` calls `go-odds` for equity at flop/turn/river
- Prometheus metrics on both services; Jaeger tracing for Java→Go calls
- Docker Compose: one-command local startup
- GitHub Actions CI: build + test both services

---

## Hard scope (OUT — do NOT add)

| Feature | Why out |
|---------|---------|
| Real money / payments / withdrawals | Legal + out of scope |
| Multi-table / tournaments | Scope creep |
| Spectator / observer mode | Nice-to-have, deferred |
| Reconnection token / resume mid-hand | Complex; document the gap |
| Chat / emotes | Not engineering signal |
| Leaderboard / rankings | Deferred |
| Bot / AI opponent | Deferred (go-odds can add later) |
| React / Vue frontend | Static HTML only for demo |
| Kubernetes / cloud deploy | Docker Compose is enough |
| Anti-cheat / rate limiting | Deferred |
| Mobile clients | Out of scope |

---

## Tech stack

### java-api
- Java 21 (virtual threads via `spring.threads.virtual.enabled=true`)
- Spring Boot 3.x
- Spring Security (JWT resource server, no sessions)
- Spring WebSocket + STOMP (simple in-memory broker, single node)
- Spring Data JPA + Hibernate
- PostgreSQL 16
- Flyway (schema migrations)
- springdoc-openapi (Swagger UI auto-generated)
- Spring Boot Actuator (health + `/actuator/prometheus`)
- Micrometer + OpenTelemetry SDK
- JUnit 5, MockMvc, Spring Security Test, Testcontainers

### go-odds
- Go 1.22+
- `net/http` or Gin for HTTP (health/metrics only)
- gRPC + protobuf for `OddsService`
- `pgx/v5` for PostgreSQL (odds run history)
- Prometheus Go client
- `net/http/pprof`
- OpenTelemetry Go SDK
- `go test` + `httptest` + Testcontainers-go

### Infrastructure
- PostgreSQL 16
- Prometheus
- Grafana
- Jaeger (all-in-one)
- Docker Compose

---

## Domain rules (reference)

- Hand rankings (high to low): Royal Flush, Straight Flush, Four of a Kind,
  Full House, Flush, Straight, Three of a Kind, Two Pair, One Pair, High Card
- Minimum raise = size of the last raise (or big blind if first raise)
- All-in side pots: each side pot settled separately at showdown
- Button rotates clockwise each hand; SB = button+1, BB = button+2 in 3+ player games
- Heads-up: dealer = SB, other player = BB

---

## Milestones

| Week | Goal | Done when… |
|------|------|------------|
| 1 | Auth, table REST, DB schema | `POST /auth/register`, `/auth/login`, `POST /tables`, `POST /tables/{id}/join` all return correct responses; migrations run |
| 2 | WebSocket + poker core | Two browser sessions see real-time state updates through an entire hand |
| 3 | go-odds + gRPC + observability | Equity shown at flop/turn/river; Grafana and Jaeger show data |
| 4 | Polish, demo, CI | `docker compose up` boots everything; CI green; demo video recorded |

---

## Definition of done (per task)

1. Code compiles with no warnings
2. At least one happy-path test and one error-path test
3. Flyway migration (if schema changed)
4. README updated (if API surface changed)

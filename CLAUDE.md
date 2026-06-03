# poker-engine — Claude Standing Instructions

> **Read this first, every session.**
> This file is the single source of truth for how Claude should behave in this repo.

---

## Standing Rules (always follow)

1. **Follow the 30-day plan below, day by day.** Do not skip days or merge them unless the user explicitly says so.
2. **Push to GitHub at the end of every day's work.** Commit with a descriptive message, then `git push origin main`. Never leave a day's work uncommitted.
3. **Stay in scope.** See `ASSUMPTIONS.md`. Do not add features that are listed under "Hard scope OUT".
4. **All tests must be green before pushing.** Run `./mvnw test` (Java) or `go test ./... -race` (Go) and confirm 0 failures.
5. **Never break CI.** The `.github/workflows/ci.yml` pipeline must stay green on every push.
6. **One language per day.** Days that touch Java don't also touch Go, and vice versa — unless the day explicitly covers integration between the two.
7. **Commit messages follow this format:** `Day N: <short summary>\n\n<bullet list of what changed>`

---

## Project Overview

**Server-authoritative, single-table, play-money No-Limit Texas Hold'em.**

| Service | Language | Responsibility |
|---------|----------|----------------|
| `java-api` | Java 21 / Spring Boot 3.5.x | REST API, JWT auth, WebSocket/STOMP, hand state machine, JPA persistence |
| `go-odds` | Go 1.22+ | Hand equity (exact + Monte Carlo), gRPC, Prometheus metrics |

Full scope definition: see [`ASSUMPTIONS.md`](ASSUMPTIONS.md).

---

## Stack

| Layer | Choice |
|-------|--------|
| Java | 21 LTS |
| Spring Boot | 3.5.x latest GA |
| Build | Maven Wrapper (`./mvnw`) |
| Persistence | Spring Data JPA + Hibernate + Flyway |
| Database | PostgreSQL 16 |
| Auth | JWT (JJWT 0.12.x), stateless, HMAC-SHA256 |
| WebSocket | Spring WebSocket + STOMP, in-memory broker |
| Go | 1.22+ |
| Go HTTP | `net/http` (health/metrics only) |
| Go RPC | gRPC + protobuf (`OddsService`) |
| Go DB | `pgx/v5` |
| Observability | Spring Actuator + Micrometer + Prometheus; OTel for traces |
| CI | GitHub Actions |
| Local deploy | Docker Compose |

---

## 30-Day Plan

### Week 1 — Java foundation: auth, tables, DB

| Day | Focus | Deliverable |
|-----|-------|-------------|
| **1** ✅ | Repo scaffold | Monorepo layout, proto IDL, Docker Compose skeleton, GitHub Actions CI |
| **2** ✅ | Value objects | `Card`, `Rank`, `Suit`, `Street`, `ActionType`, `HandStatus`, `TableStatus` (Java + Go) |
| **3** ✅ | DB layer | Flyway `V1__init.sql` (7 tables), JPA entities, 7 repositories |
| **4** ✅ | App wired | Testcontainers integration tests, `GlobalExceptionHandler`, `SecurityConfig` stub |
| **5** ✅ | Write endpoints | `POST /tables`, `POST /tables/{id}/seats`, `TableService` with business rules |
| **6** ✅ | Read endpoints | `GET /tables` (with `?status` filter), `GET /tables/{id}` with seat list |
| **7** ✅ | JWT auth | `POST /auth/register`, `POST /auth/login`, `JwtAuthFilter`, `SecurityConfig` locked down |

### Week 2 — Poker core + coaching analytics

The platform is a **practice table with a built-in coach**: the engine records hands, and after every decision the system tells the player what the better action would have been and why. Long-term stats build a player profile with coaching suggestions. Imported hand histories from PokerStars/GGPoker also feed the stats.

| Day | Focus | Deliverable |
|-----|-------|-------------|
| **8** | Hand lifecycle — start | `POST /tables/{id}/hands` — starts a hand, deals hole cards into a `HandSnapshot`, enforces 2–6 seated players |
| **9** | Action endpoint + live feedback | `POST /hands/{id}/actions` — records action, returns `ActionFeedback` (equity, pot odds, recommended action, explanation) |
| **10** | Stats service | `StatsComputationService` computes VPIP/PFR/aggression from `HandAction` history; `GET /players/{id}/stats` |
| **11** | Player profile + coaching | `PlayerProfileService` classifies player type (TAG/LAG/NIT/etc.) + rule-based coaching suggestions; `GET /players/{id}/profile` |
| **12** | PokerStars import | `PokerStarsParser`, `HandImportService`, `POST /import/hands` multipart upload |
| **13** | GGPoker import + V2 migration | `GGPokerParser`, `V2__analytics.sql` (source column + hand_imports table), import status endpoint |
| **14** | Tests | Parser unit tests, stats computation tests, profile tests, `FlywayMigrationTest` for V2 |

### Week 3 — Go odds service + gRPC + observability

| Day | Focus | Deliverable |
|-----|-------|-------------|
| **15** | Go project init | `go-odds` module, Gin health/readiness routes, Prometheus `/metrics`, structured `slog` logging |
| **16** | gRPC server | Implement `OddsService.Calculate` from proto; basic Monte Carlo equity for 2 players |
| **17** | Exact equity | Full exhaustive evaluator for ≤ 2 board cards remaining; Rank-based hand comparator |
| **18** | Monte Carlo | Configurable sample count; concurrency with `sync.WaitGroup`; timeout context |
| **19** | Java → Go gRPC call | `OddsClient` bean in Java; call at flop/turn/river; include equity in `HandSnapshot` payload |
| **20** | Go tests | Handler tests (`httptest`), equity evaluator unit tests, `go test -race` clean |
| **21** | Go Dockerfile + CI | Multi-stage Go Dockerfile; update CI `go-odds` job to build + test the real binary |

### Week 4 — Integration, observability, polish, release

| Day | Focus | Deliverable |
|-----|-------|-------------|
| **22** | Full Docker Compose | `postgres + java-api + go-odds` with healthchecks; `docker compose up` starts everything |
| **23** | End-to-end integration | Compose smoke test in CI; seed script inserts two players and starts a hand |
| **24** | Java observability | Actuator `/health`, `/prometheus`; custom `Counter` for hands started/finished; structured JSON logs |
| **25** | Go observability | Prometheus counters for gRPC calls/errors; Jaeger trace propagation from Java → Go |
| **26** | OpenAPI docs | `springdoc-openapi`; Swagger UI at `/swagger-ui.html`; annotate all controllers |
| **27** | README deep-dive | Architecture diagram (Mermaid), quick-start, API examples, WebSocket demo instructions |
| **28** | Demo assets | Postman collection (`docs/poker-engine.postman_collection.json`), demo script (`docs/demo-script.md`) |
| **29** | Static HTML client | Minimal `index.html` in `java-api/src/main/resources/static/` — connects via STOMP, shows table state |
| **30** | Final polish | Full demo run-through, fix any bugs, git tag `v1.0.0`, update README with badge + screenshot |

---

## Current Progress

```
Week 1: ████████████████████████  Days 1-7 complete ✅
Week 2: ████████████████████████  Days 8-14 complete ✅
Week 3: ████████████████████████  Days 15-21 complete ✅
Week 4: ████████████████████████  Days 22-30 complete ✅  🎉 v1.0.0 tagged
```

**Test count at last push: 269 Java + all Go tests, 0 failures**

---

## Key API Surface

| Method | Path | Auth | Status |
|--------|------|------|--------|
| POST | `/auth/register` | ❌ | ✅ Done |
| POST | `/auth/login` | ❌ | ✅ Done |
| GET | `/tables` | ❌ | ✅ Done |
| GET | `/tables/{id}` | ❌ | ✅ Done |
| POST | `/tables` | ✅ | ✅ Done |
| POST | `/tables/{id}/seats` | ✅ | ✅ Done |
| POST | `/tables/{id}/hands` | ✅ | ✅ Done |
| POST | `/hands/{id}/actions` | ✅ | ✅ Done |
| GET | `/players/{id}/stats` | ❌ | ✅ Done |
| GET | `/players/{id}/profile` | ❌ | ✅ Done |
| POST | `/import/hands` | ✅ | ✅ Done |
| GET | `/import/hands` | ✅ | ✅ Done |
| WS | `/ws` → `/topic/tables/{id}` | ❌ | ✅ Done |

---

## Repository Layout

```
poker-engine/
├── java-api/                  # Spring Boot service
│   ├── src/main/java/com/poker/
│   │   ├── config/            # SecurityConfig
│   │   ├── domain/
│   │   │   ├── entity/        # JPA entities
│   │   │   ├── model/         # Value objects (enums)
│   │   │   └── repository/    # Spring Data repos
│   │   ├── exception/         # BusinessRuleException, ResourceNotFoundException
│   │   ├── security/          # JwtService, JwtAuthFilter, JwtProperties
│   │   ├── service/           # TableService, AuthService
│   │   └── web/
│   │       ├── advice/        # GlobalExceptionHandler
│   │       ├── controller/    # TableController, AuthController
│   │       └── dto/           # Request/Response records
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/      # V1__init.sql
│   └── src/test/java/com/poker/
│       ├── FlywayMigrationTest.java
│       ├── JavaApiApplicationTests.java
│       ├── security/          # JwtServiceTest
│       ├── service/           # TableServiceTest, AuthServiceTest
│       └── web/controller/    # TableControllerTest, AuthControllerTest
├── go-odds/                   # Go equity service
├── proto/                     # Protobuf IDL
├── docs/                      # Architecture docs
├── .github/workflows/ci.yml   # GitHub Actions
├── docker-compose.yml         # Local dev stack
├── ASSUMPTIONS.md             # Scope freeze document
└── CLAUDE.md                  # ← this file
```

---

## Definition of Done (per day)

1. Code compiles with zero errors
2. At least one happy-path test + one error-path test for every new endpoint
3. Flyway migration added if schema changed
4. All existing tests still pass (`./mvnw test` → 0 failures)
5. Committed and pushed to `origin/main`

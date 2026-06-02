# Poker Engine — Demo Script

Walkthrough for live demonstrations. Estimated time: **10–15 minutes**.

---

## Prerequisites

```bash
git clone https://github.com/pzhiheng/poker-engine.git
cd poker-engine
docker compose up --build   # wait ~60 s for java-api to be healthy
```

Verify everything is up:

```bash
curl -s http://localhost:8080/actuator/health | jq .status   # → "UP"
curl -s http://localhost:8081/health                          # → {"status":"ok"}
```

---

## Part 1 — Core API (5 min)

### 1.1 Register players

```bash
# Register alice — note the JWT in the response
curl -sX POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice1234"}' | jq .

# Register bob
curl -sX POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"bob12345"}' | jq .
```

> **Talking point:** Stateless JWT auth. No session state on the server — each request is self-contained. Spring Security validates the HMAC-SHA256 signature on every call.

### 1.2 Login and save token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice1234"}' | jq -r '.token')
echo "Token: $TOKEN"
```

### 1.3 Create a table

```bash
TABLE=$(curl -s -X POST http://localhost:8080/tables \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Demo Table","smallBlind":5,"bigBlind":10}')
echo $TABLE | jq .
TABLE_ID=$(echo $TABLE | jq -r '.id')
```

### 1.4 Seat both players (run docs/seed.sh or do manually)

```bash
# Quick way:
POKER_API=http://localhost:8080 docs/seed.sh
```

Or via Postman: import `docs/poker-engine.postman_collection.json`, run folder **00 · Setup**.

---

## Part 2 — Live Coaching Feedback (5 min)

### 2.1 Start a hand

```bash
HAND=$(curl -s -X POST "http://localhost:8080/tables/$TABLE_ID/hands" \
  -H "Authorization: Bearer $TOKEN")
HAND_ID=$(echo $HAND | jq -r '.handId')
echo "My hole cards: $(echo $HAND | jq -r '.myHoleCards')"
```

> **Talking point:** The server deals all 5 community cards up front (stored in the snapshot, not revealed), ensuring the deck cannot be manipulated after the fact. Hole cards for opponents are masked as `["**","**"]`.

### 2.2 Make a decision — see real-time feedback

```bash
curl -sX POST "http://localhost:8080/hands/$HAND_ID/actions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"actionType":"CALL","amount":5}' | jq .feedback
```

Expected response shape:
```json
{
  "actionTaken":        "CALL",
  "recommendedAction":  "RAISE",
  "equity":             0.72,
  "potOdds":            0.25,
  "quality":            "SUBOPTIMAL",
  "explanation":        "You had 72% equity and were only getting 25% pot odds..."
}
```

> **Talking point:** The feedback is computed **synchronously** in the same HTTP request:
> 1. Action is validated and recorded in Postgres.
> 2. `DecisionEvaluatorService` calls `GrpcEquityProvider`.
> 3. go-odds runs a concurrent Monte Carlo simulation (10 000 trials, `runtime.NumCPU()` workers).
> 4. The equity result drives the GTO heuristic to pick a recommended action.
> 5. The explanation is generated from a rule table — no LLM needed.

---

## Part 3 — Analytics (2 min)

Play a few more hands (or use `docs/seed.sh` repeatedly on new tables), then:

```bash
# Raw stats
curl -s "http://localhost:8080/players/$ALICE_ID/stats" | jq .

# Full coaching profile
curl -s "http://localhost:8080/players/$ALICE_ID/profile" | jq '{playerType,suggestions}'
```

> **Talking point:** Stats are computed from the `hand_actions` table — every action is stored with its street (`PREFLOP/FLOP/TURN/RIVER`) so VPIP (preflop voluntary), PFR (preflop raise), and aggression factor (postflop) can all be derived. A player needs ≥ 30 hands before the classifier returns a type other than `UNKNOWN`.

---

## Part 4 — Observability (2 min)

Start the observability stack first:

```bash
docker compose --profile observability up --build
```

| Service | URL | Show |
|---------|-----|------|
| Swagger UI | http://localhost:8080/swagger-ui.html | Click Authorize → paste token → try any endpoint live |
| Prometheus | http://localhost:9090 | Query `poker_hands_started_total` or `poker_grpc_requests_total` |
| Grafana | http://localhost:3000 (admin/poker) | Add a panel with `rate(poker_grpc_requests_total[1m])` |
| Jaeger | http://localhost:16686 | Select service `java-api` → find a `POST /hands/*/actions` trace |

> **Talking point for Jaeger:** The `TraceContextClientInterceptor` injects the W3C `traceparent` header into every outgoing gRPC call. The Go service reads it via `otelgrpc.NewServerHandler()` and creates a child span. In Jaeger you'll see the full Java HTTP span → Go gRPC span chain in a single trace.

---

## Part 5 — Code highlights (1 min each, pick any)

### A · Concurrent Monte Carlo in Go (`go-odds/internal/evaluator/sim.go`)
Each worker gets its own seeded RNG (`rand.NewPCG`) and scratch buffers (zero allocations in the inner loop). Context cancellation is checked between 500-trial batches — the simulation honours HTTP request timeouts.

### B · Server-authoritative snapshot model (`HandService`)
`HandSnapshot` stores the complete game state as a JSON blob after every action. No reconstructing state from a long event list — just read the latest snapshot. Replay is free.

### C · Conditional gRPC injection (`GrpcConfig`, `GrpcEquityProvider`)
`@ConditionalOnProperty(go-odds.enabled=true)` means the bean doesn't exist in tests. `StubEquityProvider` auto-registers instead. The 269 Java tests never touch a network socket.

### D · Rule-based coaching (`DecisionEvaluatorService`)
Six GTO heuristics (fold < pot odds, call ≈ pot odds, raise when equity >> pot odds, …). Intentionally simple — no ML, just legible rules that can be explained to beginners.

---

## Automated end-to-end test

The full flow above is scripted in `docs/seed.sh` and runs as the **smoke-test** CI job on every push. Check the badge at the top of the README for the current status.

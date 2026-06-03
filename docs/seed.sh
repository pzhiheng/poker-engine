#!/usr/bin/env bash
# docs/seed.sh — Smoke-test seed script.
#
# Registers two players (alice + bob), creates a table, seats both players,
# and starts a hand.  Exits 0 on success, non-zero on any failure.
#
# Usage:
#   POKER_API=http://localhost:8080 docs/seed.sh
#
# Requires: curl, jq
set -euo pipefail

BASE="${POKER_API:-http://localhost:8080}"

# ── 1. Register alice ─────────────────────────────────────────────────────────
echo "==> [1/6] Registering alice..."
alice=$(curl -sf -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice1234"}')
ALICE_ID=$(echo "$alice"    | jq -r '.playerId')
ALICE_TOKEN=$(echo "$alice" | jq -r '.token')
echo "    playerId = $ALICE_ID"

# ── 2. Register bob ───────────────────────────────────────────────────────────
echo "==> [2/6] Registering bob..."
bob=$(curl -sf -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"bob12345"}')
BOB_ID=$(echo "$bob" | jq -r '.playerId')
echo "    playerId = $BOB_ID"

# ── 3. Create table (5/10 blinds) ────────────────────────────────────────────
echo "==> [3/6] Creating table (5/10 blinds)..."
table=$(curl -sf -X POST "$BASE/tables" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name":"Smoke Test","smallBlind":5,"bigBlind":10}')
TABLE_ID=$(echo "$table" | jq -r '.id')
echo "    tableId = $TABLE_ID"

# ── 4. Seat alice at seat 1 ───────────────────────────────────────────────────
echo "==> [4/6] Seating alice at seat 1 (buy-in 500)..."
curl -sf -X POST "$BASE/tables/$TABLE_ID/seats" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"playerId\":\"$ALICE_ID\",\"seatNo\":1,\"buyIn\":500}" \
  | jq -c '{seatNo,stackChips}'

# ── 5. Seat bob at seat 2 ─────────────────────────────────────────────────────
echo "==> [5/6] Seating bob at seat 2 (buy-in 500)..."
curl -sf -X POST "$BASE/tables/$TABLE_ID/seats" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"playerId\":\"$BOB_ID\",\"seatNo\":2,\"buyIn\":500}" \
  | jq -c '{seatNo,stackChips}'

# ── 6. Start a hand ───────────────────────────────────────────────────────────
echo "==> [6/6] Starting hand..."
hand=$(curl -s -o /tmp/hand_response.json -w "%{http_code}" \
  -X POST "$BASE/tables/$TABLE_ID/hands" \
  -H "Authorization: Bearer $ALICE_TOKEN")
echo "    HTTP $hand"
if [ "$hand" != "201" ]; then
  echo "    ERROR body: $(cat /tmp/hand_response.json)"
  exit 1
fi
hand=$(cat /tmp/hand_response.json)
HAND_ID=$(echo "$hand" | jq -r '.handId')
echo "    handId  = $HAND_ID"
echo "    street  = $(echo "$hand" | jq -r '.street')"
echo "    pot     = $(echo "$hand" | jq -r '.potChips') chips"

echo ""
echo "✅  Smoke test passed!"
echo "    Table : $TABLE_ID"
echo "    Hand  : $HAND_ID"

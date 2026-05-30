package evaluator_test

import (
	"math/rand/v2"
	"testing"

	"github.com/poker/go-odds/internal/evaluator"
)

// mustSim builds a Sim or fatals the test.
func mustSim(t *testing.T, players [][]string, boardStrs []string) *evaluator.Sim {
	t.Helper()
	p := make([][]evaluator.Card, len(players))
	for i, h := range players {
		cards, err := evaluator.ParseMany(h)
		if err != nil {
			t.Fatalf("parse player %d: %v", i, err)
		}
		p[i] = cards
	}
	board, err := evaluator.ParseMany(boardStrs)
	if err != nil {
		t.Fatalf("parse board: %v", err)
	}
	sim, err := evaluator.NewSim(p, board)
	if err != nil {
		t.Fatalf("NewSim: %v", err)
	}
	return sim
}

func seededRNG() *rand.Rand { return rand.New(rand.NewPCG(42, 0)) }

// ── NewSim validation ─────────────────────────────────────────────────────────

func TestNewSim_TooFewPlayers(t *testing.T) {
	_, err := evaluator.NewSim(
		[][]evaluator.Card{{evaluator.MustParse("Ah"), evaluator.MustParse("Kd")}},
		nil,
	)
	if err == nil {
		t.Error("expected error for 1 player")
	}
}

func TestNewSim_DuplicateCard(t *testing.T) {
	p := [][]evaluator.Card{
		{evaluator.MustParse("Ah"), evaluator.MustParse("Kd")},
		{evaluator.MustParse("Ah"), evaluator.MustParse("Qc")}, // duplicate Ah
	}
	_, err := evaluator.NewSim(p, nil)
	if err == nil {
		t.Error("expected error for duplicate card")
	}
}

// ── Run: totals add up ────────────────────────────────────────────────────────

func TestSim_Run_TrialsTotalCorrect(t *testing.T) {
	sim := mustSim(t,
		[][]string{{"Ah", "Kd"}, {"Qh", "Jd"}},
		[]string{"2c", "7s", "Td"}, // flop
	)
	const trials = 1000
	results := sim.Run(trials, seededRNG())

	for i, r := range results {
		total := r.WinCount + r.TieCount + r.LoseCount
		if total != trials {
			t.Errorf("player %d: win+tie+lose=%d, want %d", i, total, trials)
		}
	}
}

// ── Run: strong favourite wins most ──────────────────────────────────────────

func TestSim_Run_StrongFavouriteWinsMost(t *testing.T) {
	// AKs vs 72o on a rainbow flop where AK has top pair + top kicker
	// Board: Ah 3d 7c — AK has top pair; 72 has middle pair
	sim := mustSim(t,
		[][]string{{"Ac", "Kd"}, {"7h", "2d"}},
		[]string{"As", "3d", "8c"},
	)
	const trials = 5000
	results := sim.Run(trials, seededRNG())

	winPct := results[0].WinPct(trials)
	if winPct < 0.60 {
		t.Errorf("Ac Kd vs 7h 2d on Ah 3d 8c: expected hero win %% > 60%%, got %.2f%%", winPct*100)
	}
}

// ── Run: symmetric heads-up (same hand) approaches 50% ───────────────────────

func TestSim_Run_SymmetricHandsApproachFiftyPct(t *testing.T) {
	// AK vs AK: board has no help for either → ~50% win, ~50% tie
	sim := mustSim(t,
		[][]string{{"Ah", "Kh"}, {"Ac", "Kc"}},
		[]string{"2d", "5s", "9h"}, // rainbow board, no help
	)
	const trials = 10_000
	results := sim.Run(trials, seededRNG())

	// Combined (win + tie/2) should be close to 50% for each player.
	for i, r := range results {
		equity := float64(r.WinCount) + float64(r.TieCount)*0.5
		pct := equity / float64(trials)
		if pct < 0.40 || pct > 0.60 {
			t.Errorf("player %d: equity=%.2f%%, want ~50%%", i, pct*100)
		}
	}
}

// ── Run: zero trials ─────────────────────────────────────────────────────────

func TestSim_Run_ZeroTrials(t *testing.T) {
	sim := mustSim(t,
		[][]string{{"Ah", "Kd"}, {"Qh", "Jd"}},
		[]string{"2c", "7s", "Td"},
	)
	results := sim.Run(0, seededRNG())
	for i, r := range results {
		if r.WinCount+r.TieCount+r.LoseCount != 0 {
			t.Errorf("player %d: expected 0 counts for 0 trials", i)
		}
	}
}

// ── Pocket aces vs 72o pre-flop: classic 85% favourite ───────────────────────

func TestSim_Run_PocketAcesVs72Preflop(t *testing.T) {
	// No board yet — run from pre-flop
	sim := mustSim(t,
		[][]string{{"Ah", "Ad"}, {"7c", "2d"}},
		nil, // preflop: no board cards
	)
	const trials = 10_000
	results := sim.Run(trials, seededRNG())

	winPct := results[0].WinPct(trials)
	// AA vs 72o is ~85% equity pre-flop; accept wide band for MC variance
	if winPct < 0.75 || winPct > 0.95 {
		t.Errorf("AA vs 72o preflop: expected 75–95%%, got %.2f%%", winPct*100)
	}
}

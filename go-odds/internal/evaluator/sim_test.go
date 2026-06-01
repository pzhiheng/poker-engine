package evaluator_test

import (
	"math"
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

// ── RunExact: combo counts ────────────────────────────────────────────────────

func TestSim_RunExact_River_OneCombination(t *testing.T) {
	// drawNeed=0: complete 5-card board → exactly 1 runout
	sim := mustSim(t,
		[][]string{{"Ah", "Kd"}, {"Qh", "Jd"}},
		[]string{"2c", "7s", "Td", "3h", "8d"}, // full board
	)
	results, combos, err := sim.RunExact()
	if err != nil {
		t.Fatalf("RunExact: %v", err)
	}
	if combos != 1 {
		t.Errorf("combos: got %d, want 1", combos)
	}
	for i, r := range results {
		if r.WinCount+r.TieCount+r.LoseCount != 1 {
			t.Errorf("player %d: total=%d, want 1", i, r.WinCount+r.TieCount+r.LoseCount)
		}
	}
}

func TestSim_RunExact_Turn_CombosEqualDeckSize(t *testing.T) {
	// drawNeed=1: 4-card board (turn) — one card to come
	// deck = 52 − 2 − 2 − 4 = 44 cards  →  44 runouts
	sim := mustSim(t,
		[][]string{{"Ah", "Kd"}, {"Qh", "Jd"}},
		[]string{"2c", "7s", "Td", "3h"}, // turn board
	)
	results, combos, err := sim.RunExact()
	if err != nil {
		t.Fatalf("RunExact: %v", err)
	}
	want := 52 - 2 - 2 - 4 // 44
	if combos != want {
		t.Errorf("combos: got %d, want %d", combos, want)
	}
	for i, r := range results {
		if r.WinCount+r.TieCount+r.LoseCount != combos {
			t.Errorf("player %d: total=%d, want %d", i, r.WinCount+r.TieCount+r.LoseCount, combos)
		}
	}
}

func TestSim_RunExact_Flop_CombosEqualChoose2(t *testing.T) {
	// drawNeed=2: 3-card board (flop) — two cards to come
	// deck = 52 − 2 − 2 − 3 = 45  →  C(45,2) = 990 runouts
	sim := mustSim(t,
		[][]string{{"Ah", "Kd"}, {"Qh", "Jd"}},
		[]string{"2c", "7s", "Td"}, // flop board
	)
	results, combos, err := sim.RunExact()
	if err != nil {
		t.Fatalf("RunExact: %v", err)
	}
	n := 52 - 2 - 2 - 3 // 45
	want := n * (n - 1) / 2 // 990
	if combos != want {
		t.Errorf("combos: got %d, want %d", combos, want)
	}
	for i, r := range results {
		if r.WinCount+r.TieCount+r.LoseCount != combos {
			t.Errorf("player %d: total=%d, want %d", i, r.WinCount+r.TieCount+r.LoseCount, combos)
		}
	}
}

func TestSim_RunExact_ExactCombosMatchesActual(t *testing.T) {
	// ExactCombos() must agree with the actual count returned by RunExact().
	for _, board := range [][]string{
		{"2c", "7s", "Td", "3h", "8d"}, // river  (drawNeed=0)
		{"2c", "7s", "Td", "3h"},        // turn   (drawNeed=1)
		{"2c", "7s", "Td"},              // flop   (drawNeed=2)
	} {
		sim := mustSim(t, [][]string{{"Ah", "Kd"}, {"Qh", "Jd"}}, board)
		predicted := sim.ExactCombos()
		_, actual, err := sim.RunExact()
		if err != nil {
			t.Fatalf("RunExact board=%v: %v", board, err)
		}
		if predicted != actual {
			t.Errorf("board=%v: ExactCombos=%d, actual=%d", board, predicted, actual)
		}
	}
}

func TestSim_RunExact_PreflopsReturnsError(t *testing.T) {
	// drawNeed=5 (no board cards) → not supported for exact
	sim := mustSim(t,
		[][]string{{"Ah", "Ad"}, {"Kh", "Kd"}},
		nil, // preflop
	)
	_, _, err := sim.RunExact()
	if err == nil {
		t.Error("expected error for drawNeed=5")
	}
}

// ── RunExact: equity correctness ──────────────────────────────────────────────

func TestSim_RunExact_MatchesMonteCarlo(t *testing.T) {
	// Exact and MC equity for AK vs QJ on a 2-7-T flop should agree within 3%.
	players := [][]string{{"Ah", "Kd"}, {"Qh", "Jd"}}
	board := []string{"2c", "7s", "Td"}

	simE := mustSim(t, players, board)
	simM := mustSim(t, players, board)

	exactResults, exactCombos, err := simE.RunExact()
	if err != nil {
		t.Fatalf("RunExact: %v", err)
	}
	const mcTrials = 20_000
	mcResults := simM.Run(mcTrials, rand.New(rand.NewPCG(42, 0)))

	for i := range exactResults {
		exactWin := exactResults[i].WinPct(exactCombos)
		mcWin := mcResults[i].WinPct(mcTrials)
		diff := math.Abs(exactWin - mcWin)
		if diff > 0.03 {
			t.Errorf("player %d: exact=%.4f MC=%.4f diff=%.4f > 0.03", i, exactWin, mcWin, diff)
		}
	}
}

func TestSim_RunExact_DominantHandWinsMajority(t *testing.T) {
	// AK (top pair kings) vs 72 (no pair) on A-K-2 board — hero should win > 80%.
	sim := mustSim(t,
		[][]string{{"Ac", "Kd"}, {"7h", "2h"}},
		[]string{"As", "Ks", "2c"}, // flop: both have strong hands but AK has 2-pair
	)
	results, combos, err := sim.RunExact()
	if err != nil {
		t.Fatalf("RunExact: %v", err)
	}
	winPct := results[0].WinPct(combos)
	if winPct < 0.60 {
		t.Errorf("AK on AK2 board: expected exact win > 60%%, got %.2f%%", winPct*100)
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

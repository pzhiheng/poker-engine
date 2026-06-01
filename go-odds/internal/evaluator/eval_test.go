package evaluator_test

import (
	"testing"

	"github.com/poker/go-odds/internal/evaluator"
)

// ── Category ordering ─────────────────────────────────────────────────────────

func TestCategory_Ordering(t *testing.T) {
	// Each category must beat the one below it.
	cats := []evaluator.Category{
		evaluator.HighCard,
		evaluator.OnePair,
		evaluator.TwoPair,
		evaluator.ThreeOfAKind,
		evaluator.Straight,
		evaluator.Flush,
		evaluator.FullHouse,
		evaluator.FourOfAKind,
		evaluator.StraightFlush,
	}
	for i := 1; i < len(cats); i++ {
		if cats[i] <= cats[i-1] {
			t.Errorf("category ordering wrong: %v should beat %v", cats[i], cats[i-1])
		}
	}
}

// ── eval5 via Best5 ───────────────────────────────────────────────────────────

func best(t *testing.T, notations ...string) evaluator.HandRank {
	t.Helper()
	cards, err := evaluator.ParseMany(notations)
	if err != nil {
		t.Fatalf("parse error: %v", err)
	}
	return evaluator.Best5(cards)
}

func TestBest5_StraightFlush(t *testing.T) {
	r := best(t, "9h", "8h", "7h", "6h", "5h")
	if r.Category != evaluator.StraightFlush {
		t.Errorf("got %v, want StraightFlush", r.Category)
	}
	if r.Ranks[0] != evaluator.Nine {
		t.Errorf("straight-flush high: got %v, want Nine", r.Ranks[0])
	}
}

func TestBest5_RoyalFlush(t *testing.T) {
	// Royal Flush = Ace-high straight flush
	r := best(t, "Ah", "Kh", "Qh", "Jh", "Th")
	if r.Category != evaluator.StraightFlush {
		t.Errorf("got %v, want StraightFlush (Royal)", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("royal flush high: got %v, want Ace", r.Ranks[0])
	}
}

func TestBest5_WheelStraightFlush(t *testing.T) {
	// A-2-3-4-5 of same suit (wheel straight flush, high=5)
	r := best(t, "Ah", "2h", "3h", "4h", "5h")
	if r.Category != evaluator.StraightFlush {
		t.Errorf("got %v, want StraightFlush (wheel)", r.Category)
	}
	if r.Ranks[0] != evaluator.Five {
		t.Errorf("wheel SF high: got %v, want Five", r.Ranks[0])
	}
}

func TestBest5_FourOfAKind(t *testing.T) {
	r := best(t, "Ah", "Ad", "Ac", "As", "Kh")
	if r.Category != evaluator.FourOfAKind {
		t.Errorf("got %v, want FourOfAKind", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("quads rank: got %v, want Ace", r.Ranks[0])
	}
	if r.Ranks[1] != evaluator.King {
		t.Errorf("quads kicker: got %v, want King", r.Ranks[1])
	}
}

func TestBest5_FullHouse(t *testing.T) {
	r := best(t, "Kh", "Kd", "Kc", "Qh", "Qd")
	if r.Category != evaluator.FullHouse {
		t.Errorf("got %v, want FullHouse", r.Category)
	}
	if r.Ranks[0] != evaluator.King {
		t.Errorf("FH trips: got %v, want King", r.Ranks[0])
	}
	if r.Ranks[1] != evaluator.Queen {
		t.Errorf("FH pair: got %v, want Queen", r.Ranks[1])
	}
}

func TestBest5_Flush(t *testing.T) {
	r := best(t, "Ah", "Qh", "Jh", "9h", "2h")
	if r.Category != evaluator.Flush {
		t.Errorf("got %v, want Flush", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("flush high: got %v, want Ace", r.Ranks[0])
	}
}

func TestBest5_Straight(t *testing.T) {
	r := best(t, "9c", "8d", "7h", "6s", "5c")
	if r.Category != evaluator.Straight {
		t.Errorf("got %v, want Straight", r.Category)
	}
	if r.Ranks[0] != evaluator.Nine {
		t.Errorf("straight high: got %v, want Nine", r.Ranks[0])
	}
}

func TestBest5_WheelStraight(t *testing.T) {
	r := best(t, "Ac", "2d", "3h", "4s", "5c")
	if r.Category != evaluator.Straight {
		t.Errorf("got %v, want Straight (wheel)", r.Category)
	}
	if r.Ranks[0] != evaluator.Five {
		t.Errorf("wheel high: got %v, want Five", r.Ranks[0])
	}
}

func TestBest5_ThreeOfAKind(t *testing.T) {
	r := best(t, "Qh", "Qd", "Qc", "Ah", "Kd")
	if r.Category != evaluator.ThreeOfAKind {
		t.Errorf("got %v, want ThreeOfAKind", r.Category)
	}
	if r.Ranks[0] != evaluator.Queen {
		t.Errorf("trips rank: got %v, want Queen", r.Ranks[0])
	}
}

func TestBest5_TwoPair(t *testing.T) {
	r := best(t, "Ah", "Ad", "Kh", "Kd", "Qc")
	if r.Category != evaluator.TwoPair {
		t.Errorf("got %v, want TwoPair", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("high pair: got %v, want Ace", r.Ranks[0])
	}
	if r.Ranks[1] != evaluator.King {
		t.Errorf("low pair: got %v, want King", r.Ranks[1])
	}
	if r.Ranks[2] != evaluator.Queen {
		t.Errorf("kicker: got %v, want Queen", r.Ranks[2])
	}
}

func TestBest5_OnePair(t *testing.T) {
	r := best(t, "Ah", "Ad", "Kh", "Qd", "Jc")
	if r.Category != evaluator.OnePair {
		t.Errorf("got %v, want OnePair", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("pair rank: got %v, want Ace", r.Ranks[0])
	}
}

func TestBest5_HighCard(t *testing.T) {
	r := best(t, "Ah", "Kd", "Qc", "Jh", "9s")
	if r.Category != evaluator.HighCard {
		t.Errorf("got %v, want HighCard", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("high card: got %v, want Ace", r.Ranks[0])
	}
}

// ── 7-card best-hand selection ────────────────────────────────────────────────

func TestBest5_SevenCards_PicksFlushOverOnePair(t *testing.T) {
	// 5 diamonds + pair of Aces → best is the flush (5 diamonds)
	r := best(t, "Ah", "Ad", "2d", "5d", "8d", "Jd", "Kd")
	if r.Category != evaluator.Flush {
		t.Errorf("got %v, want Flush from 7 cards", r.Category)
	}
}

func TestBest5_SevenCards_PicksStraightFlush(t *testing.T) {
	// Straight flush available alongside a full house
	r := best(t, "9h", "8h", "7h", "6h", "5h", "9d", "9c")
	if r.Category != evaluator.StraightFlush {
		t.Errorf("got %v, want StraightFlush from 7 cards", r.Category)
	}
}

// ── Comparison ────────────────────────────────────────────────────────────────

func TestHandRank_GreaterThan(t *testing.T) {
	flush := best(t, "Ah", "Qh", "Jh", "9h", "2h")
	straight := best(t, "9c", "8d", "7h", "6s", "5c")

	if !flush.GreaterThan(straight) {
		t.Error("Flush should beat Straight")
	}
	if straight.GreaterThan(flush) {
		t.Error("Straight should not beat Flush")
	}
}

func TestHandRank_EqualTo_SameHand(t *testing.T) {
	h1 := best(t, "Ah", "Kd", "Qc", "Jh", "9s")
	h2 := best(t, "Ac", "Ks", "Qd", "Jd", "9c") // same ranks, different suits
	if !h1.EqualTo(h2) {
		t.Errorf("identical high-card hands should be equal: %+v vs %+v", h1, h2)
	}
}

func TestHandRank_KickerBreaksTie(t *testing.T) {
	// Both have a pair of Aces; one has King kicker, other has Queen kicker
	betterKicker := best(t, "Ah", "Ad", "Kh", "Qd", "Jc") // pair A, K-Q-J kickers
	worseKicker := best(t, "Ac", "As", "Qh", "Jd", "9c")   // pair A, Q-J-9 kickers
	if !betterKicker.GreaterThan(worseKicker) {
		t.Error("better kicker should win the pair of Aces tie")
	}
}

// ── Cross-category table tests ────────────────────────────────────────────────

// TestHandRanking_CategoryOrder verifies that every category beats all lower
// ones using a representative hand for each.
func TestHandRanking_CategoryOrder(t *testing.T) {
	hands := []struct {
		name  string
		cards []string
	}{
		{"HighCard", []string{"Ah", "Kd", "Qc", "Jh", "9s"}},
		{"OnePair", []string{"Ah", "Ad", "Kh", "Qd", "Jc"}},
		{"TwoPair", []string{"Ah", "Ad", "Kh", "Kd", "Qc"}},
		{"ThreeOfAKind", []string{"Ah", "Ad", "Ac", "Kh", "Qd"}},
		{"Straight", []string{"9c", "8d", "7h", "6s", "5c"}},
		{"Flush", []string{"Ah", "Qh", "Jh", "9h", "2h"}},
		{"FullHouse", []string{"Kh", "Kd", "Kc", "Qh", "Qd"}},
		{"FourOfAKind", []string{"Ah", "Ad", "Ac", "As", "Kh"}},
		{"StraightFlush", []string{"9h", "8h", "7h", "6h", "5h"}},
	}

	ranks := make([]evaluator.HandRank, len(hands))
	for i, h := range hands {
		ranks[i] = best(t, h.cards...)
	}

	for i := 1; i < len(ranks); i++ {
		if !ranks[i].GreaterThan(ranks[i-1]) {
			t.Errorf("%s should beat %s", hands[i].name, hands[i-1].name)
		}
		if ranks[i-1].GreaterThan(ranks[i]) {
			t.Errorf("%s should NOT beat %s", hands[i-1].name, hands[i].name)
		}
	}
}

// TestHandRanking_FullHouseOrdering verifies that a higher trips beats a lower
// full house, and that pair tiebreaks trips.
func TestHandRanking_FullHouseOrdering(t *testing.T) {
	acesOverKings := best(t, "Ah", "Ad", "Ac", "Kh", "Kd") // AAA KK
	kingsOverAces := best(t, "Kh", "Kd", "Kc", "Ah", "Ad") // KKK AA

	if !acesOverKings.GreaterThan(kingsOverAces) {
		t.Error("AAA KK should beat KKK AA")
	}

	kingsOverQueens := best(t, "Kh", "Kd", "Kc", "Qh", "Qd") // KKK QQ
	if !kingsOverAces.GreaterThan(kingsOverQueens) {
		t.Error("KKK AA should beat KKK QQ")
	}
}

// TestHandRanking_FlushKickerOrdering verifies kicker comparison within Flush.
func TestHandRanking_FlushKickerOrdering(t *testing.T) {
	aceHighFlush := best(t, "Ah", "Qh", "Jh", "9h", "2h") // A-Q-J-9-2 of hearts
	kingHighFlush := best(t, "Kh", "Qh", "Jh", "9h", "2h") // K-Q-J-9-2 of hearts

	if !aceHighFlush.GreaterThan(kingHighFlush) {
		t.Error("A-high flush should beat K-high flush")
	}
}

// TestBest5_SixCards verifies Best5 works with 6 cards (e.g., turn).
func TestBest5_SixCards_PicksBestFive(t *testing.T) {
	// Board: Ah Kh Qh Jh Th + 2c (off-suit junk)
	// Best 5 = A-K-Q-J-T straight flush (royal)
	r := best(t, "Ah", "Kh", "Qh", "Jh", "Th", "2c")
	if r.Category != evaluator.StraightFlush {
		t.Errorf("6-card input: got %v, want StraightFlush (royal)", r.Category)
	}
	if r.Ranks[0] != evaluator.Ace {
		t.Errorf("royal SF high: got %v, want Ace", r.Ranks[0])
	}
}

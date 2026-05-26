package evaluator_test

import (
	"testing"

	"github.com/poker/go-odds/internal/evaluator"
)

// ── Parse: happy path ─────────────────────────────────────────────────────────

func TestParse_ValidNotations(t *testing.T) {
	cases := []struct {
		notation string
		rank     evaluator.Rank
		suit     evaluator.Suit
	}{
		{"Ah", evaluator.Ace, evaluator.Hearts},
		{"Kd", evaluator.King, evaluator.Diamonds},
		{"Qc", evaluator.Queen, evaluator.Clubs},
		{"Js", evaluator.Jack, evaluator.Spades},
		{"Td", evaluator.Ten, evaluator.Diamonds},
		{"9h", evaluator.Nine, evaluator.Hearts},
		{"2c", evaluator.Two, evaluator.Clubs},
	}
	for _, tc := range cases {
		t.Run(tc.notation, func(t *testing.T) {
			c, err := evaluator.Parse(tc.notation)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if c.Rank != tc.rank {
				t.Errorf("rank: got %v, want %v", c.Rank, tc.rank)
			}
			if c.Suit != tc.suit {
				t.Errorf("suit: got %v, want %v", c.Suit, tc.suit)
			}
		})
	}
}

func TestParse_RoundTrip(t *testing.T) {
	notations := []string{"Ah", "Kd", "Qc", "Js", "Td", "2c", "9s", "3h"}
	for _, n := range notations {
		c, err := evaluator.Parse(n)
		if err != nil {
			t.Fatalf("parse(%q) error: %v", n, err)
		}
		if got := c.String(); got != n {
			t.Errorf("round-trip %q: got %q", n, got)
		}
	}
}

func TestParse_AllCombinations(t *testing.T) {
	// 13 ranks × 4 suits = 52 unique cards, each should parse cleanly
	ranks := []byte{'2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A'}
	suits := []byte{'c', 'd', 'h', 's'}
	seen := map[int]bool{}
	for _, r := range ranks {
		for _, s := range suits {
			notation := string([]byte{r, s})
			c, err := evaluator.Parse(notation)
			if err != nil {
				t.Fatalf("parse(%q) unexpected error: %v", notation, err)
			}
			if c.String() != notation {
				t.Errorf("round-trip failed for %q, got %q", notation, c.String())
			}
			idx := c.Index()
			if seen[idx] {
				t.Errorf("duplicate index %d for card %q", idx, notation)
			}
			seen[idx] = true
		}
	}
	if len(seen) != 52 {
		t.Errorf("expected 52 unique indexes, got %d", len(seen))
	}
}

// ── Parse: error paths ────────────────────────────────────────────────────────

func TestParse_InvalidNotations(t *testing.T) {
	bad := []string{"", "A", "Ahh", "Xh", "AX", "1h", "ah"}
	for _, n := range bad {
		t.Run(n, func(t *testing.T) {
			_, err := evaluator.Parse(n)
			if err == nil {
				t.Errorf("expected error for %q, got nil", n)
			}
		})
	}
}

// ── MustParse ─────────────────────────────────────────────────────────────────

func TestMustParse_Panics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for invalid notation, got none")
		}
	}()
	evaluator.MustParse("XX")
}

// ── ParseMany ────────────────────────────────────────────────────────────────

func TestParseMany_ValidSlice(t *testing.T) {
	cards, err := evaluator.ParseMany([]string{"Ah", "Kd", "Qc"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(cards) != 3 {
		t.Fatalf("expected 3 cards, got %d", len(cards))
	}
	if cards[0].Rank != evaluator.Ace {
		t.Errorf("first card: expected Ace, got %v", cards[0].Rank)
	}
}

func TestParseMany_FirstErrorReturned(t *testing.T) {
	_, err := evaluator.ParseMany([]string{"Ah", "XX", "Kd"})
	if err == nil {
		t.Error("expected error for invalid card in slice")
	}
}

// ── Index uniqueness (already covered above but explicit test) ────────────────

func TestCard_Index_Range(t *testing.T) {
	c := evaluator.MustParse("2c")
	if c.Index() != 0 {
		t.Errorf("2c index: want 0, got %d", c.Index())
	}
	c2 := evaluator.MustParse("As")
	if c2.Index() != 51 {
		t.Errorf("As index: want 51, got %d", c2.Index())
	}
}

// ── Street ────────────────────────────────────────────────────────────────────

func TestStreet_HasBoardCards(t *testing.T) {
	if evaluator.Preflop.HasBoardCards() {
		t.Error("Preflop should not have board cards")
	}
	for _, s := range []evaluator.Street{
		evaluator.Flop, evaluator.Turn, evaluator.River, evaluator.Showdown,
	} {
		if !s.HasBoardCards() {
			t.Errorf("%v should have board cards", s)
		}
	}
}

func TestStreet_Next(t *testing.T) {
	want := map[evaluator.Street]evaluator.Street{
		evaluator.Preflop: evaluator.Flop,
		evaluator.Flop:    evaluator.Turn,
		evaluator.Turn:    evaluator.River,
		evaluator.River:   evaluator.Showdown,
	}
	for from, to := range want {
		got, err := from.Next()
		if err != nil {
			t.Fatalf("%v.Next() error: %v", from, err)
		}
		if got != to {
			t.Errorf("%v.Next(): got %v, want %v", from, got, to)
		}
	}
}

func TestStreet_Next_ShowdownErrors(t *testing.T) {
	_, err := evaluator.Showdown.Next()
	if err == nil {
		t.Error("expected error from Showdown.Next(), got nil")
	}
}

package evaluator

import (
	"fmt"
	"math/rand/v2"
)

// ── Simulation types ──────────────────────────────────────────────────────────

// SeatResult holds win/tie/lose counts for one player after a simulation.
type SeatResult struct {
	WinCount  int
	TieCount  int
	LoseCount int
}

// WinPct returns the fraction of trials won (0.0–1.0).
func (r SeatResult) WinPct(trials int) float64 {
	if trials == 0 {
		return 0
	}
	return float64(r.WinCount) / float64(trials)
}

// TiePct returns the fraction of trials tied.
func (r SeatResult) TiePct(trials int) float64 {
	if trials == 0 {
		return 0
	}
	return float64(r.TieCount) / float64(trials)
}

// ── Sim ───────────────────────────────────────────────────────────────────────

// Sim runs Monte Carlo equity simulations for a hold'em hand.
// Create one with NewSim; reuse across multiple Run calls.
type Sim struct {
	players  [][]Card // hole cards per player (each exactly 2 cards)
	board    []Card   // community cards already dealt (0–5)
	deck     []Card   // remaining undealt cards
	drawNeed int      // how many more board cards to deal
}

// NewSim constructs a simulation from the given hole cards and board.
// Returns an error if any card appears more than once, or if counts are invalid.
func NewSim(players [][]Card, board []Card) (*Sim, error) {
	if len(players) < 2 {
		return nil, fmt.Errorf("sim: need at least 2 players, got %d", len(players))
	}
	for i, h := range players {
		if len(h) != 2 {
			return nil, fmt.Errorf("sim: player %d must have exactly 2 hole cards, got %d", i, len(h))
		}
	}
	if len(board) > 5 {
		return nil, fmt.Errorf("sim: board has at most 5 cards, got %d", len(board))
	}

	// Build a set of all known (dealt) card indexes.
	known := make(map[int]bool, 2*len(players)+len(board))
	for i, h := range players {
		for _, c := range h {
			idx := c.Index()
			if known[idx] {
				return nil, fmt.Errorf("sim: duplicate card %s in player %d's hand", c, i)
			}
			known[idx] = true
		}
	}
	for _, c := range board {
		idx := c.Index()
		if known[idx] {
			return nil, fmt.Errorf("sim: duplicate board card %s", c)
		}
		known[idx] = true
	}

	// Build the remaining deck.
	deck := make([]Card, 0, 52-len(known))
	for r := Two; r <= Ace; r++ {
		for s := Clubs; s <= Spades; s++ {
			c := New(r, s)
			if !known[c.Index()] {
				deck = append(deck, c)
			}
		}
	}

	return &Sim{
		players:  players,
		board:    board,
		deck:     deck,
		drawNeed: 5 - len(board),
	}, nil
}

// Run executes `trials` Monte Carlo trials and returns one SeatResult per player.
// rng must not be nil; pass rand.New(rand.NewPCG(seed, 0)) for a seeded source.
//
// For Day 16 this is single-threaded; concurrency is added in Day 18.
func (s *Sim) Run(trials int, rng *rand.Rand) []SeatResult {
	results := make([]SeatResult, len(s.players))
	if trials <= 0 || s.drawNeed < 0 {
		return results
	}

	// Working copy of the deck (shuffled in-place per trial).
	drawDeck := make([]Card, len(s.deck))
	copy(drawDeck, s.deck)

	fullBoard := make([]Card, 5)
	copy(fullBoard, s.board)

	hand7 := make([]Card, 7) // reused per trial

	for t := 0; t < trials; t++ {
		// Partial Fisher–Yates: move `drawNeed` random cards to the front.
		for i := 0; i < s.drawNeed; i++ {
			j := i + rng.IntN(len(drawDeck)-i)
			drawDeck[i], drawDeck[j] = drawDeck[j], drawDeck[i]
		}
		copy(fullBoard[len(s.board):], drawDeck[:s.drawNeed])

		// Evaluate each player's best 7-card hand.
		ranks := make([]HandRank, len(s.players))
		for p, h := range s.players {
			copy(hand7[:5], fullBoard)
			copy(hand7[5:], h)
			ranks[p] = Best5(hand7)
		}

		// Determine the best rank across all players.
		bestRank := ranks[0]
		for _, r := range ranks[1:] {
			if r.GreaterThan(bestRank) {
				bestRank = r
			}
		}

		// Award wins / ties.
		winnerCount := 0
		for _, r := range ranks {
			if r.EqualTo(bestRank) {
				winnerCount++
			}
		}

		if winnerCount == 1 {
			// Single winner.
			for p, r := range ranks {
				if r.EqualTo(bestRank) {
					results[p].WinCount++
				} else {
					results[p].LoseCount++
				}
			}
		} else {
			// Tie between winnerCount players.
			for p, r := range ranks {
				if r.EqualTo(bestRank) {
					results[p].TieCount++
				} else {
					results[p].LoseCount++
				}
			}
		}
	}

	return results
}

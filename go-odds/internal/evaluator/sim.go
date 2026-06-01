package evaluator

import (
	"context"
	"fmt"
	"math/rand/v2"
	"sync"
	"time"
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

// Sim runs Monte Carlo or exact equity calculations for a hold'em hand.
// Create one with NewSim.  All exported methods are safe to call concurrently
// because Sim carries only read-only state after construction.
type Sim struct {
	players  [][]Card // hole cards per player (each exactly 2 cards)
	board    []Card   // community cards already dealt (0–5)
	deck     []Card   // remaining undealt cards
	drawNeed int      // how many more board cards to deal (5 - len(board))
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

// ── Monte Carlo — single-threaded ─────────────────────────────────────────────

// Run executes `trials` Monte Carlo trials (single-threaded) and returns one
// SeatResult per player.  Use RunConcurrent for multi-core throughput.
func (s *Sim) Run(trials int, rng *rand.Rand) []SeatResult {
	results := make([]SeatResult, len(s.players))
	if trials <= 0 {
		return results
	}
	drawDeck := make([]Card, len(s.deck))
	copy(drawDeck, s.deck)
	board5 := make([]Card, 5)
	copy(board5, s.board)
	hand7 := make([]Card, 7)
	ranks := make([]HandRank, len(s.players))

	s.runN(trials, rng, board5, hand7, drawDeck, ranks, results)
	return results
}

// ── Monte Carlo — concurrent ──────────────────────────────────────────────────

// RunConcurrent splits `trials` across `workers` goroutines, each with its own
// seeded RNG.  Results are merged and returned.
//
// ctx is forwarded to each worker; if it is cancelled, workers stop between
// batches and the function returns with ctx.Err() (and whatever partial counts
// were accumulated so far).
//
// workers ≤ 1 falls back to the single-threaded Run path.
func (s *Sim) RunConcurrent(ctx context.Context, trials, workers int) ([]SeatResult, error) {
	if trials <= 0 {
		return make([]SeatResult, len(s.players)), nil
	}
	if workers <= 1 {
		rng := rand.New(rand.NewPCG(uint64(time.Now().UnixNano()), 0))
		return s.Run(trials, rng), ctx.Err()
	}

	// Divide work evenly; first (extra) goroutines get one extra trial.
	base := trials / workers
	extra := trials % workers

	type partial struct{ res []SeatResult }
	resultCh := make(chan partial, workers)

	var wg sync.WaitGroup
	baseSeed := uint64(time.Now().UnixNano())

	for w := 0; w < workers; w++ {
		n := base
		if w < extra {
			n++
		}
		wg.Add(1)
		go func(workerIdx, n int) {
			defer wg.Done()

			rng := rand.New(rand.NewPCG(baseSeed+uint64(workerIdx), uint64(workerIdx)))

			// Per-goroutine scratch allocations (no sharing with other goroutines).
			drawDeck := make([]Card, len(s.deck))
			copy(drawDeck, s.deck)
			board5 := make([]Card, 5)
			copy(board5, s.board)
			hand7 := make([]Card, 7)
			ranks := make([]HandRank, len(s.players))
			res := make([]SeatResult, len(s.players))

			const batchSize = 500
			done := 0
			for done < n {
				// Check for cancellation between batches.
				select {
				case <-ctx.Done():
					resultCh <- partial{res}
					return
				default:
				}

				b := batchSize
				if done+b > n {
					b = n - done
				}
				s.runN(b, rng, board5, hand7, drawDeck, ranks, res)
				done += b
			}
			resultCh <- partial{res}
		}(w, n)
	}

	// Close channel once all goroutines finish.
	go func() { wg.Wait(); close(resultCh) }()

	// Merge results from all workers.
	merged := make([]SeatResult, len(s.players))
	for p := range resultCh {
		for i, r := range p.res {
			merged[i].WinCount += r.WinCount
			merged[i].TieCount += r.TieCount
			merged[i].LoseCount += r.LoseCount
		}
	}

	return merged, ctx.Err()
}

// ── Exact enumeration ─────────────────────────────────────────────────────────

// RunExact enumerates every possible runout exhaustively and returns exact
// per-player equity.  The second return value is the total number of runouts.
//
// Supported when at most 2 board cards remain (river=0, turn→river=1,
// flop→turn+river=2).  Returns an error when more cards remain.
func (s *Sim) RunExact() ([]SeatResult, int, error) {
	results := make([]SeatResult, len(s.players))
	board5 := make([]Card, 5)
	copy(board5, s.board)
	hand7 := make([]Card, 7)
	ranks := make([]HandRank, len(s.players))
	total := 0

	switch s.drawNeed {
	case 0:
		s.scoreRunout(board5, hand7, ranks, results)
		total = 1

	case 1:
		for _, c := range s.deck {
			board5[4] = c
			s.scoreRunout(board5, hand7, ranks, results)
			total++
		}

	case 2:
		for i, c1 := range s.deck {
			board5[3] = c1
			for _, c2 := range s.deck[i+1:] {
				board5[4] = c2
				s.scoreRunout(board5, hand7, ranks, results)
				total++
			}
		}

	default:
		return nil, 0, fmt.Errorf(
			"exact evaluation requires ≤ 2 remaining board cards, have %d", s.drawNeed)
	}

	return results, total, nil
}

// ExactCombos returns how many runouts RunExact will evaluate, or -1 if exact
// evaluation is not supported for the current board state.
func (s *Sim) ExactCombos() int {
	n := len(s.deck)
	switch s.drawNeed {
	case 0:
		return 1
	case 1:
		return n
	case 2:
		return n * (n - 1) / 2
	default:
		return -1
	}
}

// ── Inner helpers ─────────────────────────────────────────────────────────────

// runN executes exactly n Monte Carlo trials, writing results into res.
// All slice arguments are caller-owned scratch buffers; runN does not allocate.
func (s *Sim) runN(n int, rng *rand.Rand,
	board5, hand7, drawDeck []Card, ranks []HandRank, res []SeatResult) {
	for i := 0; i < n; i++ {
		// Partial Fisher–Yates: bring drawNeed random cards to the front.
		for k := 0; k < s.drawNeed; k++ {
			j := k + rng.IntN(len(drawDeck)-k)
			drawDeck[k], drawDeck[j] = drawDeck[j], drawDeck[k]
		}
		copy(board5[len(s.board):], drawDeck[:s.drawNeed])
		s.scoreRunout(board5, hand7, ranks, res)
	}
}

// scoreRunout evaluates board5 (a complete 5-card board) for every player and
// increments the appropriate win/tie/lose counter in results.
// All slice arguments are pre-allocated by the caller.
func (s *Sim) scoreRunout(board5, hand7 []Card, ranks []HandRank, results []SeatResult) {
	copy(hand7[:5], board5)
	for p, h := range s.players {
		copy(hand7[5:], h)
		ranks[p] = Best5(hand7)
	}

	bestRank := ranks[0]
	for _, r := range ranks[1:] {
		if r.GreaterThan(bestRank) {
			bestRank = r
		}
	}

	winnerCount := 0
	for _, r := range ranks {
		if r.EqualTo(bestRank) {
			winnerCount++
		}
	}

	if winnerCount == 1 {
		for p, r := range ranks {
			if r.EqualTo(bestRank) {
				results[p].WinCount++
			} else {
				results[p].LoseCount++
			}
		}
	} else {
		for p, r := range ranks {
			if r.EqualTo(bestRank) {
				results[p].TieCount++
			} else {
				results[p].LoseCount++
			}
		}
	}
}

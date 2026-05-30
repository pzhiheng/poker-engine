package evaluator

import (
	"fmt"
	"sort"
)

// ── Hand categories (ascending strength) ─────────────────────────────────────

// Category ranks a 5-card poker hand from weakest (HighCard) to strongest
// (StraightFlush).  Royal Flush is represented as a Ace-high StraightFlush.
type Category uint8

const (
	HighCard      Category = 0
	OnePair       Category = 1
	TwoPair       Category = 2
	ThreeOfAKind  Category = 3
	Straight      Category = 4
	Flush         Category = 5
	FullHouse     Category = 6
	FourOfAKind   Category = 7
	StraightFlush Category = 8
)

func (c Category) String() string {
	names := [9]string{
		"HighCard", "OnePair", "TwoPair", "ThreeOfAKind",
		"Straight", "Flush", "FullHouse", "FourOfAKind", "StraightFlush",
	}
	if int(c) < len(names) {
		return names[c]
	}
	return fmt.Sprintf("Category(%d)", c)
}

// ── HandRank ──────────────────────────────────────────────────────────────────

// HandRank is a fully-ordered value type for a 5-card poker hand.
// Compare with GreaterThan / EqualTo.
//
// Ranks[0] is the primary tiebreaker (e.g. the pair rank for OnePair),
// Ranks[1..] are secondary kickers in descending significance.
type HandRank struct {
	Category Category
	Ranks    [5]Rank
}

// GreaterThan reports whether h beats other.
func (h HandRank) GreaterThan(other HandRank) bool {
	if h.Category != other.Category {
		return h.Category > other.Category
	}
	for i := range h.Ranks {
		if h.Ranks[i] != other.Ranks[i] {
			return h.Ranks[i] > other.Ranks[i]
		}
	}
	return false // equal
}

// EqualTo reports whether h and other are the same rank (a true tie).
func (h HandRank) EqualTo(other HandRank) bool {
	return h == other
}

// ── Public API ────────────────────────────────────────────────────────────────

// Best5 returns the strongest HandRank achievable from 5, 6, or 7 cards
// by evaluating all C(n, 5) combinations and returning the highest.
func Best5(cards []Card) HandRank {
	n := len(cards)
	if n < 5 || n > 7 {
		panic(fmt.Sprintf("evaluator: Best5 requires 5–7 cards, got %d", n))
	}
	var best HandRank
	var hand [5]Card
	chooseFive(cards, 0, 0, &hand, &best)
	return best
}

func chooseFive(cards []Card, start, k int, hand *[5]Card, best *HandRank) {
	n := len(cards)
	if k == 5 {
		if r := eval5(hand[:]); r.GreaterThan(*best) {
			*best = r
		}
		return
	}
	for i := start; i <= n-(5-k); i++ {
		hand[k] = cards[i]
		chooseFive(cards, i+1, k+1, hand, best)
	}
}

// ── 5-card evaluator ──────────────────────────────────────────────────────────

// eval5 evaluates exactly 5 cards.  The caller must ensure len(cards)==5.
func eval5(cards []Card) HandRank {
	// Sort descending by rank.
	s := [5]Card{}
	copy(s[:], cards)
	sort.Slice(s[:], func(i, j int) bool { return s[i].Rank > s[j].Rank })

	r := [5]Rank{s[0].Rank, s[1].Rank, s[2].Rank, s[3].Rank, s[4].Rank}

	// Flush check: all same suit.
	flush := s[0].Suit == s[1].Suit && s[1].Suit == s[2].Suit &&
		s[2].Suit == s[3].Suit && s[3].Suit == s[4].Suit

	// Rank frequency table.
	var freq [15]int // index by Rank value; Ace = 14
	for _, c := range s {
		freq[c.Rank]++
	}

	// Straight check.
	straight, strHigh := false, Rank(0)
	// Normal 5-card straight: all unique, range = 4.
	if r[0]-r[4] == 4 && freq[r[0]] == 1 && freq[r[1]] == 1 &&
		freq[r[2]] == 1 && freq[r[3]] == 1 && freq[r[4]] == 1 {
		straight, strHigh = true, r[0]
	}
	// Wheel: A-2-3-4-5  (Ace sits at r[0] because 14 > 5).
	if r[0] == Ace && r[1] == Five && r[2] == Four && r[3] == Three && r[4] == Two {
		straight, strHigh = true, Five
	}

	if straight && flush {
		return HandRank{StraightFlush, [5]Rank{strHigh}}
	}

	// Four of a kind — XXXXY or YXXXX.
	if freq[r[0]] == 4 {
		return HandRank{FourOfAKind, [5]Rank{r[0], r[4]}}
	}
	if freq[r[4]] == 4 {
		return HandRank{FourOfAKind, [5]Rank{r[4], r[0]}}
	}

	// Full house — XXXYY or YYXXX.
	if freq[r[0]] == 3 && freq[r[3]] == 2 {
		return HandRank{FullHouse, [5]Rank{r[0], r[3]}}
	}
	if freq[r[0]] == 2 && freq[r[2]] == 3 {
		return HandRank{FullHouse, [5]Rank{r[2], r[0]}}
	}

	if flush {
		return HandRank{Flush, r}
	}
	if straight {
		return HandRank{Straight, [5]Rank{strHigh}}
	}

	// Three of a kind — sorted positions: [0-2], [1-3], or [2-4].
	if freq[r[0]] == 3 {
		return HandRank{ThreeOfAKind, [5]Rank{r[0], r[3], r[4]}}
	}
	if freq[r[1]] == 3 {
		return HandRank{ThreeOfAKind, [5]Rank{r[1], r[0], r[4]}}
	}
	if freq[r[2]] == 3 {
		return HandRank{ThreeOfAKind, [5]Rank{r[2], r[0], r[1]}}
	}

	// Two pair — three layouts after sorting: AABBC, AABCC, ABBCC.
	if freq[r[0]] == 2 && freq[r[2]] == 2 { // AABBC
		return HandRank{TwoPair, [5]Rank{r[0], r[2], r[4]}}
	}
	if freq[r[0]] == 2 && freq[r[3]] == 2 { // AABCC
		return HandRank{TwoPair, [5]Rank{r[0], r[3], r[2]}}
	}
	if freq[r[1]] == 2 && freq[r[3]] == 2 { // ABBCC
		return HandRank{TwoPair, [5]Rank{r[1], r[3], r[0]}}
	}

	// One pair — pair occupies adjacent positions after sorting.
	for i := 0; i < 4; i++ {
		if r[i] == r[i+1] {
			kk := make([]Rank, 0, 3)
			for j := 0; j < 5; j++ {
				if j != i && j != i+1 {
					kk = append(kk, r[j])
				}
			}
			sort.Slice(kk, func(a, b int) bool { return kk[a] > kk[b] })
			return HandRank{OnePair, [5]Rank{r[i], kk[0], kk[1], kk[2]}}
		}
	}

	// High card.
	return HandRank{HighCard, r}
}

// Package evaluator contains the 7-card hand evaluator and its supporting types.
package evaluator

import (
	"fmt"
	"strings"
)

// ──────────────────────────────────────────────────────────────────────────────
// Suit
// ──────────────────────────────────────────────────────────────────────────────

// Suit represents one of the four card suits.
type Suit uint8

const (
	Clubs    Suit = 0
	Diamonds Suit = 1
	Hearts   Suit = 2
	Spades   Suit = 3
)

var suitByCode = map[byte]Suit{
	'c': Clubs,
	'd': Diamonds,
	'h': Hearts,
	's': Spades,
}

var suitCode = [4]byte{'c', 'd', 'h', 's'}

func (s Suit) Code() byte { return suitCode[s] }

func (s Suit) String() string {
	switch s {
	case Clubs:
		return "Clubs"
	case Diamonds:
		return "Diamonds"
	case Hearts:
		return "Hearts"
	case Spades:
		return "Spades"
	default:
		return fmt.Sprintf("Suit(%d)", uint8(s))
	}
}

// ──────────────────────────────────────────────────────────────────────────────
// Rank
// ──────────────────────────────────────────────────────────────────────────────

// Rank is the face value of a card. Two=2 … Ace=14 (ace-high only).
type Rank uint8

const (
	Two   Rank = 2
	Three Rank = 3
	Four  Rank = 4
	Five  Rank = 5
	Six   Rank = 6
	Seven Rank = 7
	Eight Rank = 8
	Nine  Rank = 9
	Ten   Rank = 10
	Jack  Rank = 11
	Queen Rank = 12
	King  Rank = 13
	Ace   Rank = 14
)

var rankByCode = map[byte]Rank{
	'2': Two, '3': Three, '4': Four, '5': Five, '6': Six,
	'7': Seven, '8': Eight, '9': Nine, 'T': Ten,
	'J': Jack, 'Q': Queen, 'K': King, 'A': Ace,
}

var rankCode = map[Rank]byte{
	Two: '2', Three: '3', Four: '4', Five: '5', Six: '6',
	Seven: '7', Eight: '8', Nine: '9', Ten: 'T',
	Jack: 'J', Queen: 'Q', King: 'K', Ace: 'A',
}

func (r Rank) Code() byte {
	if c, ok := rankCode[r]; ok {
		return c
	}
	return '?'
}

func (r Rank) String() string {
	if c, ok := rankCode[r]; ok {
		switch r {
		case Jack:
			return "Jack"
		case Queen:
			return "Queen"
		case King:
			return "King"
		case Ace:
			return "Ace"
		case Ten:
			return "Ten"
		default:
			return string(c)
		}
	}
	return fmt.Sprintf("Rank(%d)", uint8(r))
}

// ──────────────────────────────────────────────────────────────────────────────
// Card
// ──────────────────────────────────────────────────────────────────────────────

// Card is an immutable (Rank, Suit) pair.
// The zero value is invalid; always construct via Parse or New.
type Card struct {
	Rank Rank
	Suit Suit
}

// New constructs a Card directly from Rank and Suit.
func New(r Rank, s Suit) Card { return Card{Rank: r, Suit: s} }

// Parse parses a two-character notation string such as "Ah", "Td", "2c".
// Returns an error for any unrecognised input.
func Parse(notation string) (Card, error) {
	notation = strings.TrimSpace(notation)
	if len(notation) != 2 {
		return Card{}, fmt.Errorf("card notation must be exactly 2 characters, got %q", notation)
	}
	r, ok := rankByCode[notation[0]]
	if !ok {
		return Card{}, fmt.Errorf("unknown rank character %q in %q", notation[0], notation)
	}
	s, ok := suitByCode[notation[1]]
	if !ok {
		return Card{}, fmt.Errorf("unknown suit character %q in %q", notation[1], notation)
	}
	return Card{Rank: r, Suit: s}, nil
}

// MustParse is like Parse but panics on error — useful in tests and init code.
func MustParse(notation string) Card {
	c, err := Parse(notation)
	if err != nil {
		panic(err)
	}
	return c
}

// String returns the two-character notation, e.g. "Ah".
func (c Card) String() string {
	return string([]byte{c.Rank.Code(), c.Suit.Code()})
}

// Index returns a unique 0-based index in [0, 52) — useful for bitmasks.
// Index = rank_index * 4 + suit, where rank_index = rank - 2.
func (c Card) Index() int {
	return int(c.Rank-2)*4 + int(c.Suit)
}

// ──────────────────────────────────────────────────────────────────────────────
// ParseMany — convenience for parsing a slice of notations
// ──────────────────────────────────────────────────────────────────────────────

// ParseMany parses a slice of notation strings and returns the cards in order.
// The first error encountered is returned immediately.
func ParseMany(notations []string) ([]Card, error) {
	cards := make([]Card, 0, len(notations))
	for _, n := range notations {
		c, err := Parse(n)
		if err != nil {
			return nil, err
		}
		cards = append(cards, c)
	}
	return cards, nil
}

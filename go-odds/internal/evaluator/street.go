package evaluator

import "fmt"

// Street represents a betting round in a Texas Hold'em hand.
type Street string

const (
	Preflop  Street = "PREFLOP"
	Flop     Street = "FLOP"
	Turn     Street = "TURN"
	River    Street = "RIVER"
	Showdown Street = "SHOWDOWN"
)

// HasBoardCards returns true if community cards have been dealt.
func (s Street) HasBoardCards() bool {
	return s == Flop || s == Turn || s == River || s == Showdown
}

// IsEquityStreet returns true if equity calculation is meaningful.
func (s Street) IsEquityStreet() bool {
	return s == Flop || s == Turn || s == River
}

// Next returns the following street, or an error if already at Showdown.
func (s Street) Next() (Street, error) {
	switch s {
	case Preflop:
		return Flop, nil
	case Flop:
		return Turn, nil
	case Turn:
		return River, nil
	case River:
		return Showdown, nil
	default:
		return "", fmt.Errorf("no street after %q", s)
	}
}

// ExpectedBoardCards returns how many community cards should be present.
func (s Street) ExpectedBoardCards() int {
	switch s {
	case Flop:
		return 3
	case Turn:
		return 4
	case River, Showdown:
		return 5
	default:
		return 0
	}
}

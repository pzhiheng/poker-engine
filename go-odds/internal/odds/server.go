// Package odds implements the gRPC OddsService defined in proto/odds.proto.
package odds

import (
	"context"
	"log/slog"
	"math/rand/v2"
	"time"

	"github.com/poker/go-odds/internal/evaluator"
	oddspb "github.com/poker/go-odds/proto"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const defaultTrials = 50_000

// Server implements oddspb.OddsServiceServer.
type Server struct {
	oddspb.UnimplementedOddsServiceServer
}

// NewServer returns a ready-to-register OddsService server.
func NewServer() *Server { return &Server{} }

// Ping is the liveness probe for the gRPC interface.
func (s *Server) Ping(_ context.Context, _ *oddspb.PingRequest) (*oddspb.PingResponse, error) {
	return &oddspb.PingResponse{Status: "ok"}, nil
}

// CalculateEquity runs a Monte Carlo equity simulation and returns win/tie/lose
// percentages for every player in the request.
//
// Day 16: single-threaded Monte Carlo only.
// Day 17 will add the exact exhaustive path (req.Exact = true).
// Day 18 will add concurrent simulation.
func (s *Server) CalculateEquity(
	ctx context.Context, req *oddspb.EquityRequest,
) (*oddspb.EquityResponse, error) {
	start := time.Now()

	// ── Validate ──────────────────────────────────────────────────────────────
	if len(req.Players) < 2 {
		return nil, status.Errorf(codes.InvalidArgument,
			"need at least 2 players, got %d", len(req.Players))
	}

	// ── Parse board ───────────────────────────────────────────────────────────
	board, err := evaluator.ParseMany(req.BoardCards)
	if err != nil {
		return nil, status.Errorf(codes.InvalidArgument, "invalid board cards: %v", err)
	}

	// ── Parse hole cards ──────────────────────────────────────────────────────
	players := make([][]evaluator.Card, len(req.Players))
	for i, p := range req.Players {
		hc, parseErr := evaluator.ParseMany(p.HoleCards)
		if parseErr != nil {
			return nil, status.Errorf(codes.InvalidArgument,
				"seat %d: invalid hole cards: %v", p.Seat, parseErr)
		}
		players[i] = hc
	}

	// ── Build simulation ──────────────────────────────────────────────────────
	sim, err := evaluator.NewSim(players, board)
	if err != nil {
		return nil, status.Errorf(codes.InvalidArgument,
			"failed to build simulation: %v", err)
	}

	trials := int(req.Trials)
	if trials <= 0 {
		trials = defaultTrials
	}

	// ── Run ───────────────────────────────────────────────────────────────────
	var results []evaluator.SeatResult

	if req.Exact {
		// Exhaustive enumeration for ≤ 2 remaining board cards.
		exact, combos, exactErr := sim.RunExact()
		if exactErr != nil {
			return nil, status.Errorf(codes.InvalidArgument, "exact mode: %v", exactErr)
		}
		results = exact
		trials = combos
	} else {
		rng := rand.New(rand.NewPCG(uint64(time.Now().UnixNano()), 0))
		results = sim.Run(trials, rng)
	}

	// ── Build response ────────────────────────────────────────────────────────
	equities := make([]*oddspb.SeatEquity, len(req.Players))
	for i, p := range req.Players {
		r := results[i]
		equities[i] = &oddspb.SeatEquity{
			Seat:    p.Seat,
			WinPct:  r.WinPct(trials),
			TiePct:  r.TiePct(trials),
			LosePct: float64(r.LoseCount) / float64(trials),
		}
	}

	dur := time.Since(start)
	slog.Info("equity calculated",
		"hand_id", req.HandId,
		"street", req.Street,
		"players", len(req.Players),
		"trials", trials,
		"duration_ms", dur.Milliseconds(),
	)

	return &oddspb.EquityResponse{
		HandId:     req.HandId,
		Equities:   equities,
		TrialsRun:  int32(trials),
		DurationMs: dur.Milliseconds(),
	}, nil
}

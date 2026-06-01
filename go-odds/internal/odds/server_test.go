package odds_test

import (
	"context"
	"net"
	"testing"

	"github.com/poker/go-odds/internal/odds"
	oddspb "github.com/poker/go-odds/proto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

// bufSize is the in-memory connection buffer size (1 MiB).
const bufSize = 1 << 20

func newTestClient(t *testing.T) oddspb.OddsServiceClient {
	t.Helper()
	lis := bufconn.Listen(bufSize)
	srv := grpc.NewServer()
	oddspb.RegisterOddsServiceServer(srv, odds.NewServer())

	go func() { _ = srv.Serve(lis) }()
	t.Cleanup(srv.GracefulStop)

	conn, err := grpc.NewClient(
		"passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) {
			return lis.DialContext(ctx)
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("dial bufconn: %v", err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	return oddspb.NewOddsServiceClient(conn)
}

// ── Ping ──────────────────────────────────────────────────────────────────────

func TestPing_ReturnsOk(t *testing.T) {
	client := newTestClient(t)
	resp, err := client.Ping(context.Background(), &oddspb.PingRequest{})
	if err != nil {
		t.Fatalf("Ping: %v", err)
	}
	if resp.Status != "ok" {
		t.Errorf("Ping status: got %q, want %q", resp.Status, "ok")
	}
}

// ── CalculateEquity: happy path ───────────────────────────────────────────────

func TestCalculateEquity_HappyPath(t *testing.T) {
	client := newTestClient(t)

	req := &oddspb.EquityRequest{
		HandId: "test-hand-1",
		Street: "FLOP",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"7c", "2s"}},
		},
		BoardCards: []string{"As", "3d", "8c"},
		Trials:     1000,
	}

	resp, err := client.CalculateEquity(context.Background(), req)
	if err != nil {
		t.Fatalf("CalculateEquity: %v", err)
	}

	if resp.HandId != "test-hand-1" {
		t.Errorf("hand_id: got %q, want %q", resp.HandId, "test-hand-1")
	}
	if resp.TrialsRun != 1000 {
		t.Errorf("trials_run: got %d, want 1000", resp.TrialsRun)
	}
	if len(resp.Equities) != 2 {
		t.Fatalf("equities len: got %d, want 2", len(resp.Equities))
	}

	// win+tie+lose ≈ 1.0 per seat
	for _, eq := range resp.Equities {
		total := eq.WinPct + eq.TiePct + eq.LosePct
		if total < 0.99 || total > 1.01 {
			t.Errorf("seat %d: win+tie+lose=%.4f, want ~1.0", eq.Seat, total)
		}
	}
}

func TestCalculateEquity_DefaultTrials(t *testing.T) {
	// trials=0 should use server default (50 000)
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		HandId: "default-trials",
		Street: "FLOP",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: []string{"Ac", "3d", "8c"},
		Trials:     0, // should use default
	}
	resp, err := client.CalculateEquity(context.Background(), req)
	if err != nil {
		t.Fatalf("CalculateEquity: %v", err)
	}
	if resp.TrialsRun != 50_000 {
		t.Errorf("trials_run: got %d, want 50000", resp.TrialsRun)
	}
}

func TestCalculateEquity_StrongFavourite(t *testing.T) {
	// AK top pair vs 72 bottom pair: hero should win > 60%
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		HandId: "favourite-hand",
		Street: "FLOP",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ac", "Kd"}},
			{Seat: 2, HoleCards: []string{"7h", "2d"}},
		},
		BoardCards: []string{"As", "3s", "8c"},
		Trials:     3000,
	}
	resp, err := client.CalculateEquity(context.Background(), req)
	if err != nil {
		t.Fatalf("CalculateEquity: %v", err)
	}
	seat1Win := resp.Equities[0].WinPct
	if seat1Win < 0.55 {
		t.Errorf("AK on A-3-8: expected seat 1 win > 55%%, got %.2f%%", seat1Win*100)
	}
}

// ── CalculateEquity: validation errors ───────────────────────────────────────

func TestCalculateEquity_TooFewPlayers(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		Players:    []*oddspb.PlayerHand{{Seat: 1, HoleCards: []string{"Ah", "Kd"}}},
		BoardCards: []string{"2c", "7s", "Td"},
		Trials:     100,
	}
	_, err := client.CalculateEquity(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for 1 player")
	}
	if st, _ := status.FromError(err); st.Code() != codes.InvalidArgument {
		t.Errorf("status: got %v, want InvalidArgument", st.Code())
	}
}

func TestCalculateEquity_InvalidBoardCard(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: []string{"XX", "7s", "Td"}, // invalid card
		Trials:     100,
	}
	_, err := client.CalculateEquity(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for invalid board card")
	}
}

// ── Exact mode ───────────────────────────────────────────────────────────────

func TestCalculateEquity_ExactMode_Flop(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		HandId: "exact-flop",
		Street: "FLOP",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: []string{"2c", "7s", "Td"},
		Exact:      true,
	}
	resp, err := client.CalculateEquity(context.Background(), req)
	if err != nil {
		t.Fatalf("CalculateEquity exact: %v", err)
	}
	// C(45,2) = 990 runouts
	if resp.TrialsRun != 990 {
		t.Errorf("trials_run: got %d, want 990 (C(45,2))", resp.TrialsRun)
	}
	for _, eq := range resp.Equities {
		total := eq.WinPct + eq.TiePct + eq.LosePct
		if total < 0.99 || total > 1.01 {
			t.Errorf("seat %d: win+tie+lose=%.4f, want ~1.0", eq.Seat, total)
		}
	}
}

func TestCalculateEquity_ExactMode_Turn(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		HandId: "exact-turn",
		Street: "TURN",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: []string{"2c", "7s", "Td", "3h"},
		Exact:      true,
	}
	resp, err := client.CalculateEquity(context.Background(), req)
	if err != nil {
		t.Fatalf("CalculateEquity exact turn: %v", err)
	}
	// deck = 52 − 2 − 2 − 4 = 44 runouts
	if resp.TrialsRun != 44 {
		t.Errorf("trials_run: got %d, want 44", resp.TrialsRun)
	}
}

func TestCalculateEquity_ExactMode_PreflopReturnsError(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		HandId: "exact-preflop-error",
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "Kd"}},
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: nil, // preflop: drawNeed=5
		Exact:      true,
	}
	_, err := client.CalculateEquity(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for exact mode with preflop board")
	}
	if st, _ := status.FromError(err); st.Code() != codes.InvalidArgument {
		t.Errorf("status: got %v, want InvalidArgument", st.Code())
	}
}

func TestCalculateEquity_InvalidHoleCard(t *testing.T) {
	client := newTestClient(t)
	req := &oddspb.EquityRequest{
		Players: []*oddspb.PlayerHand{
			{Seat: 1, HoleCards: []string{"Ah", "ZZ"}}, // invalid
			{Seat: 2, HoleCards: []string{"Qh", "Jd"}},
		},
		BoardCards: []string{"2c", "7s", "Td"},
		Trials:     100,
	}
	_, err := client.CalculateEquity(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for invalid hole card")
	}
}

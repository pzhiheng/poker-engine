package telemetry_test

import (
	"context"
	"errors"
	"testing"

	dto "github.com/prometheus/client_model/go"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/poker/go-odds/internal/telemetry"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// ── Helpers ───────────────────────────────────────────────────────────────────

func noopHandler(_ context.Context, _ any) (any, error) { return "ok", nil }

func grpcErrHandler(_ context.Context, _ any) (any, error) {
	return nil, status.Error(codes.InvalidArgument, "bad input")
}

func goErrHandler(_ context.Context, _ any) (any, error) {
	return nil, errors.New("unexpected") // wrapped as codes.Unknown
}

func unaryInfo(method string) *grpc.UnaryServerInfo {
	return &grpc.UnaryServerInfo{FullMethod: "/poker.OddsService/" + method}
}

// counterValue returns the current total count for a counter metric with the
// given name and label set. Because promauto registers at package-init time,
// values are cumulative in a single test binary; tests use before/after deltas.
func counterValue(name string, labels map[string]string) float64 {
	mfs, err := prometheus.DefaultGatherer.Gather()
	if err != nil {
		return 0
	}
	for _, mf := range mfs {
		if mf.GetName() != name {
			continue
		}
		for _, m := range mf.GetMetric() {
			if labelsMatch(m.GetLabel(), labels) {
				c := m.GetCounter()
				if c != nil {
					return c.GetValue()
				}
			}
		}
	}
	return 0
}

func labelsMatch(pairs []*dto.LabelPair, want map[string]string) bool {
	got := make(map[string]string, len(pairs))
	for _, p := range pairs {
		got[p.GetName()] = p.GetValue()
	}
	for k, v := range want {
		if got[k] != v {
			return false
		}
	}
	return true
}

// ── Tests ─────────────────────────────────────────────────────────────────────

func TestMetricsInterceptor_SuccessIncrementsRequests(t *testing.T) {
	before := counterValue("poker_grpc_requests_total", map[string]string{"method": "CalculateEquity"})

	_, _ = telemetry.MetricsUnaryInterceptor(
		context.Background(), nil, unaryInfo("CalculateEquity"), noopHandler)

	after := counterValue("poker_grpc_requests_total", map[string]string{"method": "CalculateEquity"})
	if after-before != 1 {
		t.Errorf("want requests delta=1, got %.0f", after-before)
	}
}

func TestMetricsInterceptor_GrpcErrorIncrementsRequestsAndErrors(t *testing.T) {
	reqBefore := counterValue("poker_grpc_requests_total", map[string]string{"method": "Ping"})
	errBefore := counterValue("poker_grpc_errors_total", map[string]string{
		"method": "Ping",
		"code":   codes.InvalidArgument.String(),
	})

	_, err := telemetry.MetricsUnaryInterceptor(
		context.Background(), nil, unaryInfo("Ping"), grpcErrHandler)
	if err == nil {
		t.Fatal("expected handler error")
	}

	reqAfter := counterValue("poker_grpc_requests_total", map[string]string{"method": "Ping"})
	errAfter := counterValue("poker_grpc_errors_total", map[string]string{
		"method": "Ping",
		"code":   codes.InvalidArgument.String(),
	})
	if reqAfter-reqBefore != 1 {
		t.Errorf("want requests delta=1, got %.0f", reqAfter-reqBefore)
	}
	if errAfter-errBefore != 1 {
		t.Errorf("want errors delta=1, got %.0f", errAfter-errBefore)
	}
}

func TestMetricsInterceptor_SuccessDoesNotIncrementErrors(t *testing.T) {
	// Snapshot total errors for any CalculateEquity call (all codes)
	errBefore := counterValue("poker_grpc_errors_total", map[string]string{"method": "CalculateEquity"})

	_, _ = telemetry.MetricsUnaryInterceptor(
		context.Background(), nil, unaryInfo("CalculateEquity"), noopHandler)

	errAfter := counterValue("poker_grpc_errors_total", map[string]string{"method": "CalculateEquity"})
	if errAfter != errBefore {
		t.Errorf("expected no error counter change on success, delta=%.0f", errAfter-errBefore)
	}
}

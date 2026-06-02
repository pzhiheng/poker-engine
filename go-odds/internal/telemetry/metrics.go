// Package telemetry wires up Prometheus counters and OpenTelemetry tracing
// for the go-odds gRPC service.
package telemetry

import (
	"context"
	"path"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/status"
)

// Prometheus counters — registered in the default registry at init time.
var (
	// grpcRequestsTotal counts every inbound gRPC call, labelled by method name.
	grpcRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "poker_grpc_requests_total",
			Help: "Total gRPC requests handled by the go-odds service.",
		},
		[]string{"method"},
	)

	// grpcErrorsTotal counts calls that returned a non-OK gRPC status.
	grpcErrorsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "poker_grpc_errors_total",
			Help: "Total gRPC errors in the go-odds service.",
		},
		[]string{"method", "code"},
	)
)

// MetricsUnaryInterceptor is a gRPC unary server interceptor that increments
// poker_grpc_requests_total and (on error) poker_grpc_errors_total for every
// inbound call.
func MetricsUnaryInterceptor(
	ctx context.Context,
	req any,
	info *grpc.UnaryServerInfo,
	handler grpc.UnaryHandler,
) (any, error) {
	// FullMethod is "/package.Service/Method"; Base strips everything up to "/"
	method := path.Base(info.FullMethod) // e.g., "CalculateEquity", "Ping"

	grpcRequestsTotal.WithLabelValues(method).Inc()

	resp, err := handler(ctx, req)
	if err != nil {
		st, _ := status.FromError(err)
		grpcErrorsTotal.WithLabelValues(method, st.Code().String()).Inc()
	}
	return resp, err
}

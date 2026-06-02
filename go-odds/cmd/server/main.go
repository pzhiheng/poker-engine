// Command server is the entry point for the go-odds HTTP + gRPC service.
//
// Day 15: HTTP server — health/readiness probes and Prometheus /metrics.
// Day 16: gRPC server — OddsService.CalculateEquity (Monte Carlo) + Ping.
// Day 25: telemetry — Prometheus counters + OTel/Jaeger trace propagation.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/poker/go-odds/internal/health"
	"github.com/poker/go-odds/internal/odds"
	"github.com/poker/go-odds/internal/telemetry"
	oddspb "github.com/poker/go-odds/proto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"google.golang.org/grpc"
)

func main() {
	// ── Structured JSON logging to stderr ─────────────────────────────────────
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	// ── Configuration ─────────────────────────────────────────────────────────
	httpPort := getenv("HTTP_PORT", "8081")
	grpcPort := getenv("GRPC_PORT", "50051")

	// ── HTTP server ───────────────────────────────────────────────────────────
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", health.LivenessHandler)
	mux.HandleFunc("GET /ready", health.ReadinessHandler)
	mux.Handle("GET /metrics", promhttp.Handler())

	httpSrv := &http.Server{
		Addr:         ":" + httpPort,
		Handler:      mux,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  30 * time.Second,
	}

	go func() {
		slog.Info("go-odds HTTP server starting", "port", httpPort)
		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("HTTP server failed", "err", err)
			os.Exit(1)
		}
	}()

	// ── OpenTelemetry tracing ─────────────────────────────────────────────────
	// Reads OTEL_EXPORTER_OTLP_ENDPOINT; no-op when the variable is unset.
	shutdownTracing, tracingErr := telemetry.InitTracing(context.Background())
	if tracingErr != nil {
		slog.Warn("tracing init failed — continuing without traces", "err", tracingErr)
		shutdownTracing = func() {}
	}
	defer shutdownTracing()

	// ── gRPC server ───────────────────────────────────────────────────────────
	lis, err := net.Listen("tcp", ":"+grpcPort)
	if err != nil {
		slog.Error("failed to bind gRPC port", "port", grpcPort, "err", err)
		os.Exit(1)
	}

	grpcSrv := grpc.NewServer(
		// otelgrpc v0.46+ uses a stats handler instead of an interceptor.
		// It extracts W3C traceparent from gRPC metadata and creates child
		// spans — enabling Java → Go trace correlation in Jaeger.
		grpc.StatsHandler(otelgrpc.NewServerHandler()),
		// Prometheus counters: poker_grpc_requests_total / poker_grpc_errors_total.
		grpc.ChainUnaryInterceptor(telemetry.MetricsUnaryInterceptor),
	)
	oddspb.RegisterOddsServiceServer(grpcSrv, odds.NewServer())

	go func() {
		slog.Info("go-odds gRPC server starting", "port", grpcPort)
		if err := grpcSrv.Serve(lis); err != nil {
			slog.Error("gRPC server failed", "err", err)
			os.Exit(1)
		}
	}()

	// ── Graceful shutdown on SIGINT / SIGTERM ─────────────────────────────────
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	sig := <-quit

	slog.Info("shutting down", "signal", sig.String())

	grpcSrv.GracefulStop()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpSrv.Shutdown(ctx); err != nil {
		slog.Error("HTTP shutdown failed", "err", err)
	}

	slog.Info("server stopped")
}

// getenv returns the value of the env variable named by key, or fallback.
func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

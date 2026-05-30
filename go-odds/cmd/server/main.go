// Command server is the entry point for the go-odds HTTP + gRPC service.
//
// Day 15: HTTP server with health/readiness probes and Prometheus metrics.
// gRPC server (OddsService) is wired in on Day 16.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/poker/go-odds/internal/health"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

func main() {
	// ── Structured JSON logging to stderr ─────────────────────────────────────
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	// ── Configuration ─────────────────────────────────────────────────────────
	httpPort := getenv("HTTP_PORT", "8081")
	addr := ":" + httpPort

	// ── Routes ────────────────────────────────────────────────────────────────
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health",  health.LivenessHandler)
	mux.HandleFunc("GET /ready",   health.ReadinessHandler)
	mux.Handle("GET /metrics",     promhttp.Handler())

	srv := &http.Server{
		Addr:         addr,
		Handler:      mux,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  30 * time.Second,
	}

	// ── Start server ──────────────────────────────────────────────────────────
	go func() {
		slog.Info("go-odds HTTP server starting",
			"addr", addr,
			"routes", []string{"GET /health", "GET /ready", "GET /metrics"},
		)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("server failed", "err", err)
			os.Exit(1)
		}
	}()

	// ── Graceful shutdown on SIGINT / SIGTERM ─────────────────────────────────
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	sig := <-quit

	slog.Info("shutting down server", "signal", sig.String())
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("graceful shutdown failed", "err", err)
		os.Exit(1)
	}
	slog.Info("server stopped")
}

// getenv returns the value of the environment variable named by key,
// or fallback if the variable is unset or empty.
func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

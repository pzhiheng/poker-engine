// Package health provides HTTP handlers for Kubernetes liveness and readiness probes.
package health

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

type statusResponse struct {
	Status string `json:"status"`
}

// LivenessHandler returns 200 OK as long as the process is running.
//
// Route: GET /health
func LivenessHandler(w http.ResponseWriter, r *http.Request) {
	slog.Debug("liveness probe", "method", r.Method, "remote", r.RemoteAddr)
	writeJSON(w, http.StatusOK, statusResponse{Status: "ok"})
}

// ReadinessHandler returns 200 OK when the service is ready to accept traffic.
// Extend this to check downstream dependencies (gRPC listener, DB) as they come online.
//
// Route: GET /ready
func ReadinessHandler(w http.ResponseWriter, r *http.Request) {
	slog.Debug("readiness probe", "method", r.Method, "remote", r.RemoteAddr)
	writeJSON(w, http.StatusOK, statusResponse{Status: "ok"})
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("failed to write JSON response", "err", err)
	}
}

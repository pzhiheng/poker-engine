package health_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/poker/go-odds/internal/health"
)

// buildMux mirrors the HTTP mux wired in cmd/server/main.go.
// Keeping this in the health package means we can test route behaviour
// without importing cmd/server (which has an init that opens a port).
func buildMux() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", health.LivenessHandler)
	mux.HandleFunc("GET /ready", health.ReadinessHandler)
	return mux
}

// ── Route existence ───────────────────────────────────────────────────────────

func TestMux_HealthRoute_Returns200(t *testing.T) {
	mux := buildMux()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()
	mux.ServeHTTP(rr, req)
	if rr.Code != http.StatusOK {
		t.Errorf("GET /health: got %d, want 200", rr.Code)
	}
}

func TestMux_ReadyRoute_Returns200(t *testing.T) {
	mux := buildMux()
	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	rr := httptest.NewRecorder()
	mux.ServeHTTP(rr, req)
	if rr.Code != http.StatusOK {
		t.Errorf("GET /ready: got %d, want 200", rr.Code)
	}
}

// ── Unknown routes return 404 ─────────────────────────────────────────────────

func TestMux_UnknownRoute_Returns404(t *testing.T) {
	paths := []string{"/", "/metrics", "/status", "/ping", "/unknown"}
	mux := buildMux()

	for _, path := range paths {
		t.Run(path, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, path, nil)
			rr := httptest.NewRecorder()
			mux.ServeHTTP(rr, req)
			if rr.Code != http.StatusNotFound {
				t.Errorf("GET %s: got %d, want 404", path, rr.Code)
			}
		})
	}
}

// ── Wrong method returns 405 (Go 1.22 mux method routing) ────────────────────

func TestMux_WrongMethod_Returns405(t *testing.T) {
	cases := []struct {
		method string
		path   string
	}{
		{http.MethodPost, "/health"},
		{http.MethodPut, "/health"},
		{http.MethodDelete, "/health"},
		{http.MethodPost, "/ready"},
	}
	mux := buildMux()

	for _, tc := range cases {
		t.Run(tc.method+" "+tc.path, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, nil)
			rr := httptest.NewRecorder()
			mux.ServeHTTP(rr, req)
			if rr.Code != http.StatusMethodNotAllowed {
				t.Errorf("%s %s: got %d, want 405", tc.method, tc.path, rr.Code)
			}
		})
	}
}

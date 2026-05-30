package health_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/poker/go-odds/internal/health"
)

// ── Liveness ──────────────────────────────────────────────────────────────────

func TestLivenessHandler_Returns200(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()

	health.LivenessHandler(rr, req)

	if rr.Code != http.StatusOK {
		t.Errorf("status: got %d, want %d", rr.Code, http.StatusOK)
	}
}

func TestLivenessHandler_BodyStatusOk(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()

	health.LivenessHandler(rr, req)

	var body map[string]string
	if err := json.Unmarshal(rr.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if got := body["status"]; got != "ok" {
		t.Errorf("body.status: got %q, want %q", got, "ok")
	}
}

// ── Readiness ─────────────────────────────────────────────────────────────────

func TestReadinessHandler_Returns200(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	rr := httptest.NewRecorder()

	health.ReadinessHandler(rr, req)

	if rr.Code != http.StatusOK {
		t.Errorf("status: got %d, want %d", rr.Code, http.StatusOK)
	}
}

func TestReadinessHandler_BodyStatusOk(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	rr := httptest.NewRecorder()

	health.ReadinessHandler(rr, req)

	var body map[string]string
	if err := json.Unmarshal(rr.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if got := body["status"]; got != "ok" {
		t.Errorf("body.status: got %q, want %q", got, "ok")
	}
}

// ── Content-Type ──────────────────────────────────────────────────────────────

func TestHandlers_ContentTypeJSON(t *testing.T) {
	handlers := []struct {
		name    string
		handler http.HandlerFunc
		path    string
	}{
		{"liveness", health.LivenessHandler, "/health"},
		{"readiness", health.ReadinessHandler, "/ready"},
	}

	for _, tc := range handlers {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, tc.path, nil)
			rr := httptest.NewRecorder()

			tc.handler(rr, req)

			ct := rr.Header().Get("Content-Type")
			if ct != "application/json" {
				t.Errorf("Content-Type: got %q, want %q", ct, "application/json")
			}
		})
	}
}

// ── Response shape ────────────────────────────────────────────────────────────

func TestHandlers_ResponseIsValidJSON(t *testing.T) {
	handlers := []http.HandlerFunc{
		health.LivenessHandler,
		health.ReadinessHandler,
	}

	for _, h := range handlers {
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		rr := httptest.NewRecorder()
		h(rr, req)

		if !json.Valid(rr.Body.Bytes()) {
			t.Errorf("response body is not valid JSON: %q", rr.Body.String())
		}
	}
}

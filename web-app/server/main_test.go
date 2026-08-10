package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestAPIProxyStripsPrefixForwardsHeadersAndSetsHost(t *testing.T) {
	var gotPath, gotHost, gotAuth string
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath, gotHost, gotAuth = r.URL.Path, r.Host, r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	proxy, err := newAPIProxy(backend.URL)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/api/counter.v1.CounterService/GetCount", nil)
	req.Header.Set("Authorization", "Bearer token")
	rec := httptest.NewRecorder()
	proxy.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if want := "/counter.v1.CounterService/GetCount"; gotPath != want {
		t.Errorf("upstream path = %q, want %q (prefix stripped)", gotPath, want)
	}
	if want := backend.Listener.Addr().String(); gotHost != want {
		t.Errorf("upstream Host = %q, want %q", gotHost, want)
	}
	if gotAuth != "Bearer token" {
		t.Errorf("Authorization not forwarded: %q", gotAuth)
	}
}

func TestAPIProxyStripsInjectedIAPHeadersButKeepsAuthorization(t *testing.T) {
	var iapAssertion, iapUser, authz string
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		iapAssertion = r.Header.Get("X-Goog-Iap-Jwt-Assertion")
		iapUser = r.Header.Get("X-Goog-Authenticated-User-Email")
		authz = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	proxy, err := newAPIProxy(backend.URL)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/api/x", nil)
	req.Header.Set("X-Goog-Iap-Jwt-Assertion", "web-iap-assertion")
	req.Header.Set("X-Goog-Authenticated-User-Email", "accounts.google.com:user@example.com")
	req.Header.Set("Authorization", "Bearer keep-me")
	proxy.ServeHTTP(httptest.NewRecorder(), req)

	if iapAssertion != "" || iapUser != "" {
		t.Errorf("IAP-injected headers leaked to API: assertion=%q user=%q", iapAssertion, iapUser)
	}
	if authz != "Bearer keep-me" {
		t.Errorf("Authorization not forwarded: %q", authz)
	}
}

func TestNewAPIProxyRejectsNonAbsoluteURL(t *testing.T) {
	if _, err := newAPIProxy("/api"); err == nil {
		t.Error("expected an error for a non-absolute upstream")
	}
}

func TestSPAHandlerServesFilesAndFallsBackToIndex(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "index.html"), []byte("INDEX"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "app.js"), []byte("JS"), 0o644); err != nil {
		t.Fatal(err)
	}
	h := newSPAHandler(dir)

	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/app.js", nil))
	if body, _ := io.ReadAll(rec.Result().Body); string(body) != "JS" {
		t.Errorf("static file = %q, want JS", body)
	}

	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/some/client/route", nil))
	if body, _ := io.ReadAll(rec.Result().Body); string(body) != "INDEX" {
		t.Errorf("client-side route = %q, want INDEX fallback", body)
	}
}

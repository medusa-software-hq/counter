// Command server serves the counter web app: the built SPA bundle, and a same-origin reverse proxy
// from /api to the API service so browser traffic stays single-origin. Minting the API's IAP token
// for the proxied hop is added at the IAP flip; today it forwards requests unchanged.
package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"

	"golang.org/x/oauth2"
	"google.golang.org/api/idtoken"
)

func main() {
	port := envOr("PORT", "8080")
	staticDir := envOr("STATIC_DIR", "/srv")

	upstream := os.Getenv("COUNTER_API_UPSTREAM")
	if upstream == "" {
		log.Fatal("COUNTER_API_UPSTREAM must be set to the API service's base URL")
	}
	// When the API is IAP-gated, authenticate the proxied hop as this service's own identity with an
	// ID token minted for the API's IAP OAuth client. Empty audience ⇒ no token (the pre-flip state).
	var apiToken oauth2.TokenSource
	if audience := os.Getenv("API_IAP_AUDIENCE"); audience != "" {
		ts, err := idtoken.NewTokenSource(context.Background(), audience)
		if err != nil {
			log.Fatalf("API_IAP_AUDIENCE token source: %v", err)
		}
		apiToken = ts
	}
	apiProxy, err := newAPIProxy(upstream, apiToken)
	if err != nil {
		log.Fatalf("invalid COUNTER_API_UPSTREAM: %v", err)
	}

	mux := http.NewServeMux()
	mux.Handle("/api/", apiProxy)
	mux.Handle("/", newSPAHandler(staticDir))

	log.Printf("listening on :%s (static %s, /api -> %s)", port, staticDir, upstream)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatal(err)
	}
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// newAPIProxy reverse-proxies /api/* to the API at upstream, stripping the /api prefix and setting
// the upstream Host — Cloud Run selects the target service by Host/SNI.
func newAPIProxy(upstream string, apiToken oauth2.TokenSource) (http.Handler, error) {
	target, err := url.Parse(upstream)
	if err != nil {
		return nil, err
	}
	if target.Scheme == "" || target.Host == "" {
		return nil, fmt.Errorf("expected an absolute URL, got %q", upstream)
	}
	proxy := httputil.NewSingleHostReverseProxy(target)
	base := proxy.Director
	proxy.Director = func(r *http.Request) {
		base(r)
		r.Host = target.Host
		// This service is IAP-gated, so IAP injects the caller's identity on the inbound request.
		// Those headers must not reach the API across this hop — it authenticates the request
		// itself. The IAP flip will set the API's credentials here explicitly instead.
		r.Header.Del("X-Goog-Iap-Jwt-Assertion")
		r.Header.Del("X-Goog-Authenticated-User-Email")
		r.Header.Del("X-Goog-Authenticated-User-Id")
		// Once the API is IAP-gated, present this service's ID token to its IAP, replacing the
		// browser's token. Cached and refreshed by the source, so calling per request is cheap.
		if apiToken != nil {
			if t, err := apiToken.Token(); err == nil {
				r.Header.Set("Authorization", "Bearer "+t.AccessToken)
			}
		}
	}
	return http.StripPrefix("/api", proxy), nil
}

// newSPAHandler serves static files from dir, falling back to index.html for paths that don't map to
// a file — the SPA's client-side routes.
func newSPAHandler(dir string) http.Handler {
	files := http.FileServer(http.Dir(dir))
	index := filepath.Join(dir, "index.html")
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path := filepath.Join(dir, filepath.Clean("/"+r.URL.Path))
		if info, err := os.Stat(path); err == nil && !info.IsDir() {
			files.ServeHTTP(w, r)
			return
		}
		http.ServeFile(w, r, index)
	})
}

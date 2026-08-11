// Command server serves the counter web app: the built SPA bundle, and a same-origin reverse proxy
// from /api to the API service so browser traffic stays single-origin.
package main

import (
	"fmt"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
)

func main() {
	port := envOr("PORT", "8080")
	staticDir := envOr("STATIC_DIR", "/srv")

	upstream := os.Getenv("COUNTER_API_UPSTREAM")
	if upstream == "" {
		log.Fatal("COUNTER_API_UPSTREAM must be set to the API service's base URL")
	}
	apiProxy, err := newAPIProxy(upstream)
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
// the upstream Host so the platform routes by Host/SNI.
func newAPIProxy(upstream string) (http.Handler, error) {
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

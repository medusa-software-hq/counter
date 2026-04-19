package main

import (
	"log"
	"net/http"
	"os"
)

func main() {
	port := os.Getenv("PORT")

	if port == "" {
		log.Fatalf("PORT environment variable must be set")
	}

	frontendDir := os.Getenv("FRONTEND_DIST_DIR")

	if frontendDir == "" {
		log.Fatalf("FRONTEND_DIST_DIR environment variable must be set")
	}

	mux := http.NewServeMux()

	// Serve the frontend SPA — static files with index.html fallback.
	fs := http.FileServer(http.Dir(frontendDir))
	mux.HandleFunc("GET /", func(w http.ResponseWriter, r *http.Request) {
		// Try to serve the file directly. If it doesn't exist, serve index.html
		// so that client-side routing works.
		path := frontendDir + r.URL.Path

		if _, err := os.Stat(path); os.IsNotExist(err) && r.URL.Path != "/" {
			http.ServeFile(w, r, frontendDir+"/index.html")
			return
		}

		fs.ServeHTTP(w, r)
	})

	addr := ":" + port

	log.Printf("listening on %s", addr)

	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

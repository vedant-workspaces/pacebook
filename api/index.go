// Package handler is the Vercel serverless entry point. vercel.json rewrites
// every /api/* request (and Search Console verification files) here, also
// passing the original path as ?__pl_path= so routing works whether or not
// the platform preserves the original URL.
package handler

import (
	"context"
	"log/slog"
	"net/http"
	"strings"
	"sync"

	"github.com/vedant-workspaces/pacebook/backend/app"
)

var (
	mu      sync.Mutex
	handler http.Handler
)

// get builds the app once per instance. A failed start (e.g. the database
// was briefly unreachable) is retried on the next request rather than
// cached forever.
func get(ctx context.Context) (http.Handler, error) {
	mu.Lock()
	defer mu.Unlock()
	if handler != nil {
		return handler, nil
	}
	h, err := app.New(ctx)
	if err != nil {
		return nil, err
	}
	handler = h
	return h, nil
}

func Handler(w http.ResponseWriter, r *http.Request) {
	h, err := get(r.Context())
	if err != nil {
		slog.Error("startup failed", "err", err)
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":{"code":"INTERNAL_ERROR","message":"Pacelog is starting up. Please try again in a moment."}}`)) //nolint:errcheck
		return
	}
	h.ServeHTTP(w, restorePath(r))
}

const pathParam = "__pl_path"

// restorePath puts back the original request path if the rewrite replaced
// it, and removes the helper query parameter either way.
func restorePath(r *http.Request) *http.Request {
	q := r.URL.Query()
	orig := q.Get(pathParam)
	if orig == "" {
		return r
	}
	q.Del(pathParam)
	r2 := r.Clone(r.Context())
	if !strings.HasPrefix(r.URL.Path, "/api/") || r.URL.Path == "/api/index" {
		if strings.HasPrefix(orig, "/") && !strings.Contains(orig, "..") {
			r2.URL.Path = orig
			r2.URL.RawPath = ""
		}
	}
	r2.URL.RawQuery = q.Encode()
	r2.RequestURI = r2.URL.RequestURI()
	return r2
}

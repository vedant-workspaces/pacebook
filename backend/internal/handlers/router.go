package handlers

import (
	"html"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/vedant-workspaces/pacebook/backend/config"
	"github.com/vedant-workspaces/pacebook/backend/internal/auth"
	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/middleware"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

// NewRouter wires every route and the middleware chain.
func NewRouter(cfg *config.Config, pool *pgxpool.Pool) http.Handler {
	store := repository.New(pool)
	sessions := auth.NewSessions(store, cfg.SessionSecret, cfg.SessionTTL, cfg.IsProduction())

	authH := &AuthHandler{store: store, sessions: sessions, frontendURL: cfg.FrontendURL, devLogin: cfg.DevAuthEnabled}
	if cfg.GoogleEnabled() {
		authH.google = auth.NewGoogle(cfg.GoogleClientID, cfg.GoogleClientSecret, cfg.GoogleRedirectURL, cfg.IsProduction())
	}
	training := &TrainingHandler{services.NewTrainingService(store)}
	activities := &ActivityHandler{services.NewActivityService(store)}
	stats := &StatsHandler{services.NewStatsService(store)}

	mux := http.NewServeMux()
	protected := middleware.RequireAuth(sessions)
	handle := func(pattern string, h http.HandlerFunc) { mux.Handle(pattern, protected(h)) }

	// Public.
	mux.HandleFunc("GET /api/health", func(w http.ResponseWriter, r *http.Request) {
		if err := pool.Ping(r.Context()); err != nil {
			httpx.Error(w, http.StatusServiceUnavailable, httpx.CodeInternal, "Database unavailable.")
			return
		}
		httpx.Data(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("GET /api/auth/config", authH.Config)
	mux.HandleFunc("GET /api/auth/google", authH.GoogleBegin)
	mux.HandleFunc("GET /api/auth/google/callback", authH.GoogleCallback)
	mux.HandleFunc("POST /api/auth/logout", authH.Logout)
	if cfg.DevAuthEnabled && !cfg.IsProduction() {
		mux.HandleFunc("POST /api/auth/dev-login", authH.DevLogin)
	}

	// Authenticated.
	handle("GET /api/auth/me", authH.Me)
	handle("GET /api/meta", Meta)

	handle("GET /api/training-blocks", training.ListBlocks)
	handle("POST /api/training-blocks", training.CreateBlock)
	handle("GET /api/training-blocks/{id}", training.GetBlock)
	handle("PUT /api/training-blocks/{id}", training.UpdateBlock)
	handle("DELETE /api/training-blocks/{id}", training.DeleteBlock)
	handle("GET /api/training-blocks/{id}/stats", training.BlockStats)
	handle("GET /api/training-blocks/{id}/activities", training.ListPlanned)
	handle("POST /api/training-blocks/{id}/activities", training.CreatePlanned)

	handle("GET /api/training-activities/{id}", training.GetPlanned)
	handle("PUT /api/training-activities/{id}", training.UpdatePlanned)
	handle("DELETE /api/training-activities/{id}", training.DeletePlanned)
	handle("POST /api/training-activities/{id}/complete", training.Complete)
	handle("POST /api/training-activities/{id}/modify", training.Modify)
	handle("POST /api/training-activities/{id}/skip", training.Skip)
	handle("POST /api/training-activities/{id}/reset", training.Reset)

	handle("GET /api/activities", activities.List)
	handle("POST /api/activities", activities.Create)
	handle("GET /api/activities/{id}", activities.Get)
	handle("PUT /api/activities/{id}", activities.Update)
	handle("DELETE /api/activities/{id}", activities.Delete)

	handle("GET /api/stats/overview", stats.Overview)
	handle("GET /api/stats/weekly", stats.Weekly)
	handle("GET /api/stats/monthly", stats.Monthly)
	handle("GET /api/stats/yearly", stats.Yearly)
	handle("GET /api/stats/day", stats.Day)
	handle("GET /api/stats/week", stats.Week)

	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		httpx.Error(w, http.StatusNotFound, httpx.CodeNotFound, "Not found.")
	})
	if cfg.SiteVerificationFile != "" {
		name := cfg.SiteVerificationFile
		mux.HandleFunc("GET /"+name, func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			io.WriteString(w, "google-site-verification: "+name) //nolint:errcheck
		})
	}
	if cfg.StaticDir != "" {
		spa, err := spaHandler(cfg.StaticDir, cfg.SiteVerificationToken)
		if err != nil {
			slog.Error("static files unavailable", "dir", cfg.StaticDir, "err", err)
		} else {
			mux.Handle("/", spa)
		}
	}

	var h http.Handler = mux
	h = middleware.CSRF(cfg.AllowedOrigins)(h)
	h = middleware.CORS(cfg.AllowedOrigins)(h)
	h = middleware.SecurityHeaders(h)
	h = middleware.Logger(h)
	h = middleware.Recover(h)
	return h
}

// spaHandler serves the built frontend, falling back to index.html so
// client-side routes like /training/123 work on reload. index.html is held
// in memory, with the Search Console verification meta tag added if set.
func spaHandler(dir, verificationToken string) (http.Handler, error) {
	index, err := os.ReadFile(filepath.Join(dir, "index.html"))
	if err != nil {
		return nil, err
	}
	if verificationToken != "" {
		tag := `<meta name="google-site-verification" content="` + html.EscapeString(verificationToken) + `" />`
		index = []byte(strings.Replace(string(index), "<head>", "<head>\n    "+tag, 1))
	}

	files := http.FileServer(http.Dir(dir))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" && r.URL.Path != "/index.html" {
			p := filepath.Join(dir, filepath.Clean("/"+r.URL.Path))
			if info, err := os.Stat(p); err == nil && !info.IsDir() {
				if strings.HasPrefix(r.URL.Path, "/assets/") {
					w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
				}
				files.ServeHTTP(w, r)
				return
			}
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache")
		w.Write(index) //nolint:errcheck
	}), nil
}

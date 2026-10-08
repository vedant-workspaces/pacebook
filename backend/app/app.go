// Package app builds the complete Pacelog HTTP handler (config, database,
// migrations, routes). It is the entry point for serverless platforms such
// as Vercel, where there is no long-running main(); cmd/server is the
// equivalent for container hosts.
package app

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/config"
	"github.com/vedant-workspaces/pacebook/backend/internal/database"
	"github.com/vedant-workspaces/pacebook/backend/internal/handlers"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

// New loads configuration from the environment, connects to PostgreSQL,
// applies pending migrations and returns the API handler.
func New(ctx context.Context) (http.Handler, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	pool, err := database.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		return nil, err
	}
	if err := database.Migrate(ctx, pool); err != nil {
		pool.Close()
		return nil, err
	}
	// No background goroutines survive between serverless invocations, so
	// expired sessions are pruned once per cold start instead.
	if n, err := repository.New(pool).DeleteExpiredSessions(ctx); err != nil {
		slog.Warn("session cleanup failed", "err", err)
	} else if n > 0 {
		slog.Info("removed expired sessions", "count", n)
	}
	return handlers.NewRouter(cfg, pool), nil
}

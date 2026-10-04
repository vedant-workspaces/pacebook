// Command server runs the Pacebook REST API.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/config"
	"github.com/vedant-workspaces/pacebook/backend/internal/database"
	"github.com/vedant-workspaces/pacebook/backend/internal/handlers"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

func main() {
	if err := run(); err != nil {
		slog.Error("server stopped", "err", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := database.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err := database.Migrate(ctx, pool); err != nil {
		return err
	}

	go cleanupSessions(ctx, repository.New(pool))

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           handlers.NewRouter(cfg, pool),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	errc := make(chan error, 1)
	go func() {
		slog.Info("pacebook api listening", "port", cfg.Port, "env", cfg.Env,
			"google_auth", cfg.GoogleEnabled(), "dev_login", cfg.DevAuthEnabled)
		errc <- srv.ListenAndServe()
	}()

	select {
	case err := <-errc:
		if !errors.Is(err, http.ErrServerClosed) {
			return err
		}
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		return srv.Shutdown(shutdownCtx)
	}
	return nil
}

func cleanupSessions(ctx context.Context, store *repository.Store) {
	ticker := time.NewTicker(time.Hour)
	defer ticker.Stop()
	for {
		if n, err := store.DeleteExpiredSessions(ctx); err != nil && ctx.Err() == nil {
			slog.Warn("session cleanup failed", "err", err)
		} else if n > 0 {
			slog.Info("removed expired sessions", "count", n)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

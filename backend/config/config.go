// Package config loads application settings from environment variables.
package config

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Env         string
	Port        string
	DatabaseURL string

	GoogleClientID     string
	GoogleClientSecret string
	GoogleRedirectURL  string

	SessionSecret string
	SessionTTL    time.Duration

	FrontendURL    string
	AllowedOrigins []string

	// DevAuthEnabled exposes POST /api/auth/dev-login. It is refused outside
	// development so it can never be switched on in production.
	DevAuthEnabled bool

	// StaticDir, when set, makes the API also serve the built frontend.
	StaticDir string
}

func (c *Config) IsProduction() bool { return c.Env == "production" }

func (c *Config) GoogleEnabled() bool {
	return c.GoogleClientID != "" && c.GoogleClientSecret != "" && c.GoogleRedirectURL != ""
}

func Load() (*Config, error) {
	cfg := &Config{
		Env:                getenv("APP_ENV", "development"),
		Port:               getenv("PORT", "8080"),
		DatabaseURL:        os.Getenv("DATABASE_URL"),
		GoogleClientID:     os.Getenv("GOOGLE_CLIENT_ID"),
		GoogleClientSecret: os.Getenv("GOOGLE_CLIENT_SECRET"),
		GoogleRedirectURL:  os.Getenv("GOOGLE_REDIRECT_URL"),
		SessionSecret:      os.Getenv("SESSION_SECRET"),
		// On Render, the service's public URL is a sensible default.
		FrontendURL: strings.TrimRight(getenv("FRONTEND_URL", getenv("RENDER_EXTERNAL_URL", "http://localhost:5173")), "/"),
		StaticDir:   os.Getenv("STATIC_DIR"),
	}

	if cfg.GoogleRedirectURL == "" && cfg.GoogleClientID != "" {
		cfg.GoogleRedirectURL = cfg.FrontendURL + "/api/auth/google/callback"
	}

	ttlDays, err := strconv.Atoi(getenv("SESSION_TTL_DAYS", "30"))
	if err != nil || ttlDays < 1 {
		return nil, errors.New("SESSION_TTL_DAYS must be a positive integer")
	}
	cfg.SessionTTL = time.Duration(ttlDays) * 24 * time.Hour

	cfg.AllowedOrigins = []string{cfg.FrontendURL}
	for _, o := range strings.Split(os.Getenv("CORS_ALLOWED_ORIGINS"), ",") {
		if o = strings.TrimRight(strings.TrimSpace(o), "/"); o != "" && o != "*" {
			cfg.AllowedOrigins = append(cfg.AllowedOrigins, o)
		}
	}

	if cfg.DatabaseURL == "" {
		return nil, errors.New("DATABASE_URL is required")
	}

	devAuth := os.Getenv("DEV_AUTH_ENABLED") == "true"
	if cfg.IsProduction() {
		if devAuth {
			return nil, errors.New("DEV_AUTH_ENABLED cannot be used when APP_ENV=production")
		}
		if len(cfg.SessionSecret) < 32 {
			return nil, errors.New("SESSION_SECRET must be at least 32 characters in production")
		}
		if !cfg.GoogleEnabled() {
			return nil, errors.New("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required in production")
		}
	} else {
		cfg.DevAuthEnabled = devAuth
		if cfg.SessionSecret == "" {
			b := make([]byte, 32)
			if _, err := rand.Read(b); err != nil {
				return nil, fmt.Errorf("generate session secret: %w", err)
			}
			cfg.SessionSecret = hex.EncodeToString(b)
			slog.Warn("SESSION_SECRET not set; using a random secret (sessions will not survive restarts)")
		}
	}

	return cfg, nil
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

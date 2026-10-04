// Package middleware provides authentication, CORS, CSRF protection,
// logging and panic recovery.
package middleware

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"net/url"
	"runtime/debug"
	"slices"
	"strings"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/auth"
	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

type ctxKey struct{}

// UserFrom returns the authenticated user placed in ctx by RequireAuth.
func UserFrom(ctx context.Context) *models.User {
	u, _ := ctx.Value(ctxKey{}).(*models.User)
	return u
}

func WithUser(ctx context.Context, u *models.User) context.Context {
	return context.WithValue(ctx, ctxKey{}, u)
}

// RequireAuth rejects requests without a valid session with 401. The user is
// always derived from the session cookie, never from client-supplied IDs.
func RequireAuth(sessions *auth.Sessions) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			u, err := sessions.User(r)
			if err != nil {
				if !errors.Is(err, repository.ErrNotFound) {
					httpx.Internal(w, r, err)
					return
				}
				httpx.Error(w, http.StatusUnauthorized, httpx.CodeUnauthorized, "Please sign in to continue.")
				return
			}
			next.ServeHTTP(w, r.WithContext(WithUser(r.Context(), u)))
		})
	}
}

// CORS allows credentialed requests from the configured origins only.
func CORS(allowed []string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin != "" && slices.Contains(allowed, origin) {
				h := w.Header()
				h.Set("Access-Control-Allow-Origin", origin)
				h.Set("Access-Control-Allow-Credentials", "true")
				h.Add("Vary", "Origin")
				if r.Method == http.MethodOptions {
					h.Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
					h.Set("Access-Control-Allow-Headers", "Content-Type, X-Requested-With")
					h.Set("Access-Control-Max-Age", "600")
					w.WriteHeader(http.StatusNoContent)
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

// CSRFHeader is the header the frontend sends on every state-changing
// request. Browsers cannot attach it cross-site without a CORS preflight,
// which only succeeds for allowed origins.
const CSRFHeader = "X-Requested-With"

// CSRF protects unsafe methods: the custom header must be present and, if
// the browser sent an Origin, it must be an allowed one (or our own host).
// Combined with SameSite=Lax session cookies.
func CSRF(allowed []string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.Method {
			case http.MethodGet, http.MethodHead, http.MethodOptions:
				next.ServeHTTP(w, r)
				return
			}
			if r.Header.Get(CSRFHeader) == "" || !originAllowed(r, allowed) {
				httpx.Error(w, http.StatusForbidden, httpx.CodeForbidden, "Request blocked.")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func originAllowed(r *http.Request, allowed []string) bool {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return true
	}
	if slices.Contains(allowed, origin) {
		return true
	}
	u, err := url.Parse(origin)
	return err == nil && strings.EqualFold(u.Host, r.Host)
}

// SecurityHeaders sets conservative defaults on every response.
func SecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
		next.ServeHTTP(w, r)
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(code int) {
	s.status = code
	s.ResponseWriter.WriteHeader(code)
}

func Logger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		if strings.HasPrefix(r.URL.Path, "/api/") {
			slog.Info("request", "method", r.Method, "path", r.URL.Path, "status", rec.status,
				"duration_ms", time.Since(start).Milliseconds())
		}
	})
}

func Recover(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				if v == http.ErrAbortHandler {
					panic(v)
				}
				slog.Error("panic", "value", v, "stack", string(debug.Stack()))
				httpx.Error(w, http.StatusInternalServerError, httpx.CodeInternal, "Something went wrong. Please try again.")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

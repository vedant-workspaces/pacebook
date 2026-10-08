package handlers

import (
	"errors"
	"log/slog"
	"net/http"
	"strings"

	"github.com/vedant-workspaces/pacebook/backend/internal/auth"
	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/middleware"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

type AuthHandler struct {
	store       *repository.Store
	sessions    *auth.Sessions
	google      *auth.Google // nil when Google OAuth isn't configured
	frontendURL string
	devLogin    bool
}

func (h *AuthHandler) Config(w http.ResponseWriter, r *http.Request) {
	httpx.Data(w, http.StatusOK, map[string]bool{
		"googleEnabled":   h.google != nil,
		"devLoginEnabled": h.devLogin,
	})
}

func (h *AuthHandler) GoogleBegin(w http.ResponseWriter, r *http.Request) {
	if h.google == nil {
		httpx.Error(w, http.StatusNotFound, httpx.CodeNotFound, "Google sign-in is not configured.")
		return
	}
	if err := h.google.Begin(w, r); err != nil {
		httpx.Internal(w, r, err)
	}
}

func (h *AuthHandler) GoogleCallback(w http.ResponseWriter, r *http.Request) {
	if h.google == nil {
		httpx.Error(w, http.StatusNotFound, httpx.CodeNotFound, "Google sign-in is not configured.")
		return
	}
	profile, err := h.google.Finish(w, r)
	if err != nil {
		if !errors.Is(err, auth.ErrInvalidState) {
			slog.Warn("google sign-in failed", "err", err)
		}
		http.Redirect(w, r, h.frontendURL+"/login?error=signin_failed", http.StatusFound)
		return
	}
	var picture *string
	if profile.Picture != "" {
		picture = &profile.Picture
	}
	user, err := h.store.UpsertGoogleUser(r.Context(), profile.Subject, strings.ToLower(profile.Email), profile.Name, picture)
	if err != nil {
		slog.Error("upsert google user", "err", err)
		http.Redirect(w, r, h.frontendURL+"/login?error=signin_failed", http.StatusFound)
		return
	}
	cookie, err := h.sessions.Create(r.Context(), user.ID)
	if err != nil {
		slog.Error("create session", "err", err)
		http.Redirect(w, r, h.frontendURL+"/login?error=signin_failed", http.StatusFound)
		return
	}
	http.SetCookie(w, cookie)
	http.Redirect(w, r, h.frontendURL+"/", http.StatusFound)
}

// DevLogin signs in as a local test user without Google. It is only routed
// when APP_ENV=development and DEV_AUTH_ENABLED=true.
func (h *AuthHandler) DevLogin(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Email string `json:"email"`
		Name  string `json:"name"`
	}
	if !decode(w, r, &body) {
		return
	}
	email := strings.ToLower(strings.TrimSpace(body.Email))
	if email == "" {
		email = "runner@pacelog.local"
	}
	if !strings.Contains(email, "@") || len(email) > 254 {
		httpx.FieldError(w, "email", "Enter a valid email address.")
		return
	}
	name := strings.TrimSpace(body.Name)
	if name == "" {
		name = "Dev Runner"
	}
	user, err := h.store.UpsertGoogleUser(r.Context(), "dev:"+email, email, name, nil)
	if err != nil {
		fail(w, r, err)
		return
	}
	cookie, err := h.sessions.Create(r.Context(), user.ID)
	if err != nil {
		httpx.Internal(w, r, err)
		return
	}
	http.SetCookie(w, cookie)
	httpx.Data(w, http.StatusOK, user)
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	cookie, err := h.sessions.Destroy(r)
	if err != nil {
		httpx.Internal(w, r, err)
		return
	}
	http.SetCookie(w, cookie)
	httpx.Data(w, http.StatusOK, map[string]bool{"ok": true})
}

func (h *AuthHandler) Me(w http.ResponseWriter, r *http.Request) {
	httpx.Data(w, http.StatusOK, middleware.UserFrom(r.Context()))
}

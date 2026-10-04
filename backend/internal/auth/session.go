// Package auth implements Google sign-in and cookie-based sessions.
package auth

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"net/http"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

const SessionCookieName = "pacebook_session"

// Sessions issues opaque random tokens in an HttpOnly cookie. Only an HMAC
// of each token is stored, so a database leak does not leak live sessions.
type Sessions struct {
	store  *repository.Store
	secret []byte
	ttl    time.Duration
	secure bool
}

func NewSessions(store *repository.Store, secret string, ttl time.Duration, secureCookies bool) *Sessions {
	return &Sessions{store: store, secret: []byte(secret), ttl: ttl, secure: secureCookies}
}

func (s *Sessions) hash(token string) []byte {
	m := hmac.New(sha256.New, s.secret)
	m.Write([]byte(token))
	return m.Sum(nil)
}

// Create starts a session for userID and returns the cookie to set.
func (s *Sessions) Create(ctx context.Context, userID string) (*http.Cookie, error) {
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return nil, err
	}
	token := base64.RawURLEncoding.EncodeToString(raw)
	expires := time.Now().Add(s.ttl)
	if err := s.store.CreateSession(ctx, userID, s.hash(token), expires); err != nil {
		return nil, err
	}
	return s.cookie(token, expires), nil
}

func (s *Sessions) cookie(value string, expires time.Time) *http.Cookie {
	c := &http.Cookie{
		Name:     SessionCookieName,
		Value:    value,
		Path:     "/",
		HttpOnly: true,
		Secure:   s.secure,
		SameSite: http.SameSiteLaxMode,
		Expires:  expires,
	}
	if value == "" {
		c.MaxAge = -1
	}
	return c
}

// User resolves the session cookie on r to a user, or returns ErrNotFound.
func (s *Sessions) User(r *http.Request) (*models.User, error) {
	c, err := r.Cookie(SessionCookieName)
	if err != nil || c.Value == "" {
		return nil, repository.ErrNotFound
	}
	return s.store.UserForSession(r.Context(), s.hash(c.Value))
}

// Destroy deletes the session in r (if any) and returns a clearing cookie.
func (s *Sessions) Destroy(r *http.Request) (*http.Cookie, error) {
	if c, err := r.Cookie(SessionCookieName); err == nil && c.Value != "" {
		if err := s.store.DeleteSession(r.Context(), s.hash(c.Value)); err != nil {
			return nil, err
		}
	}
	return s.cookie("", time.Unix(0, 0)), nil
}

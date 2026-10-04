package auth

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

const (
	stateCookieName    = "pacebook_oauth_state"
	verifierCookieName = "pacebook_oauth_verifier"
	userInfoURL        = "https://openidconnect.googleapis.com/v1/userinfo"
)

type GoogleProfile struct {
	Subject       string `json:"sub"`
	Email         string `json:"email"`
	EmailVerified bool   `json:"email_verified"`
	Name          string `json:"name"`
	Picture       string `json:"picture"`
}

// Google runs the OAuth 2.0 authorization-code flow with state and PKCE.
type Google struct {
	config *oauth2.Config
	secure bool
}

func NewGoogle(clientID, clientSecret, redirectURL string, secureCookies bool) *Google {
	return &Google{
		config: &oauth2.Config{
			ClientID:     clientID,
			ClientSecret: clientSecret,
			RedirectURL:  redirectURL,
			Endpoint:     google.Endpoint,
			Scopes:       []string{"openid", "email", "profile"},
		},
		secure: secureCookies,
	}
}

func (g *Google) tempCookie(name, value string, maxAge int) *http.Cookie {
	return &http.Cookie{
		Name:     name,
		Value:    value,
		Path:     "/api/auth",
		HttpOnly: true,
		Secure:   g.secure,
		// Lax so the cookie is sent on Google's top-level redirect back to us.
		SameSite: http.SameSiteLaxMode,
		MaxAge:   maxAge,
	}
}

// Begin stores a random state and PKCE verifier in short-lived cookies and
// redirects the browser to Google's consent screen.
func (g *Google) Begin(w http.ResponseWriter, r *http.Request) error {
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return err
	}
	state := base64.RawURLEncoding.EncodeToString(raw)
	verifier := oauth2.GenerateVerifier()

	http.SetCookie(w, g.tempCookie(stateCookieName, state, 600))
	http.SetCookie(w, g.tempCookie(verifierCookieName, verifier, 600))
	url := g.config.AuthCodeURL(state, oauth2.AccessTypeOnline, oauth2.S256ChallengeOption(verifier),
		oauth2.SetAuthURLParam("prompt", "select_account"))
	http.Redirect(w, r, url, http.StatusFound)
	return nil
}

var ErrInvalidState = errors.New("invalid oauth state")

// Finish validates the callback's state, exchanges the code and returns the
// verified Google profile.
func (g *Google) Finish(w http.ResponseWriter, r *http.Request) (*GoogleProfile, error) {
	stateCookie, err1 := r.Cookie(stateCookieName)
	verifierCookie, err2 := r.Cookie(verifierCookieName)
	// The state is single-use: clear both cookies whatever happens next.
	http.SetCookie(w, g.tempCookie(stateCookieName, "", -1))
	http.SetCookie(w, g.tempCookie(verifierCookieName, "", -1))

	state := r.URL.Query().Get("state")
	if err1 != nil || err2 != nil || state == "" ||
		subtle.ConstantTimeCompare([]byte(stateCookie.Value), []byte(state)) != 1 {
		return nil, ErrInvalidState
	}
	if errParam := r.URL.Query().Get("error"); errParam != "" {
		return nil, fmt.Errorf("google returned error: %s", errParam)
	}
	code := r.URL.Query().Get("code")
	if code == "" {
		return nil, errors.New("missing authorization code")
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	token, err := g.config.Exchange(ctx, code, oauth2.VerifierOption(verifierCookie.Value))
	if err != nil {
		return nil, fmt.Errorf("exchange code: %w", err)
	}

	resp, err := g.config.Client(ctx, token).Get(userInfoURL)
	if err != nil {
		return nil, fmt.Errorf("fetch userinfo: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetch userinfo: status %d", resp.StatusCode)
	}
	var p GoogleProfile
	if err := json.NewDecoder(http.MaxBytesReader(nil, resp.Body, 1<<20)).Decode(&p); err != nil {
		return nil, fmt.Errorf("decode userinfo: %w", err)
	}
	if p.Subject == "" || p.Email == "" {
		return nil, errors.New("userinfo missing subject or email")
	}
	if !p.EmailVerified {
		return nil, errors.New("google email is not verified")
	}
	return &p, nil
}

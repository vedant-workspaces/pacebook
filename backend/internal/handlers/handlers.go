// Package handlers maps HTTP requests to services. Handlers only parse input,
// call a service with the session user's ID and shape the response.
package handlers

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/middleware"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

const maxBodyBytes = 1 << 20

func userID(r *http.Request) string { return middleware.UserFrom(r.Context()).ID }

// pathID returns the {id} path value. Malformed IDs are reported as 404 so
// they behave exactly like IDs that don't exist or belong to someone else.
func pathID(w http.ResponseWriter, r *http.Request) (string, bool) {
	id := r.PathValue("id")
	if !services.IsUUID(id) {
		httpx.Error(w, http.StatusNotFound, httpx.CodeNotFound, "Not found.")
		return "", false
	}
	return id, true
}

func decode(w http.ResponseWriter, r *http.Request, dst any) bool {
	body := http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if err := json.NewDecoder(body).Decode(dst); err != nil && !errors.Is(err, io.EOF) {
		msg := "Invalid request body."
		var syn *json.SyntaxError
		var typ *json.UnmarshalTypeError
		switch {
		case errors.As(err, &typ):
			msg = "Invalid value for " + typ.Field + "."
		case errors.As(err, &syn):
		default:
			// Custom unmarshalers (e.g. dates) return readable messages.
			if !errors.Is(err, io.ErrUnexpectedEOF) {
				msg = err.Error()
			}
		}
		httpx.Error(w, http.StatusBadRequest, httpx.CodeValidation, msg)
		return false
	}
	return true
}

// fail translates service errors into the API error envelope.
func fail(w http.ResponseWriter, r *http.Request, err error) {
	if v, ok := services.IsValidation(err); ok {
		httpx.FieldError(w, v.Field, v.Message)
		return
	}
	if c, ok := services.IsConflict(err); ok {
		httpx.Error(w, http.StatusConflict, httpx.CodeConflict, c.Message)
		return
	}
	if errors.Is(err, services.ErrNotFound) {
		httpx.Error(w, http.StatusNotFound, httpx.CodeNotFound, "Not found.")
		return
	}
	httpx.Internal(w, r, err)
}

// queryDate parses an optional YYYY-MM-DD query parameter.
func queryDate(w http.ResponseWriter, r *http.Request, name string) (*models.Date, bool) {
	v := r.URL.Query().Get(name)
	if v == "" {
		return nil, true
	}
	d, err := models.ParseDate(v)
	if err != nil {
		httpx.FieldError(w, name, "Invalid "+name+" (expected YYYY-MM-DD).")
		return nil, false
	}
	return &d, true
}

// today is the user's local date, sent by the client as ?today=YYYY-MM-DD.
// It falls back to the server's date.
func today(w http.ResponseWriter, r *http.Request) (models.Date, bool) {
	d, ok := queryDate(w, r, "today")
	if !ok {
		return models.Date{}, false
	}
	if d == nil {
		return models.DateOf(time.Now()), true
	}
	return *d, true
}

// dateOrToday reads ?date=, defaulting to today.
func dateOrToday(w http.ResponseWriter, r *http.Request) (models.Date, bool) {
	d, ok := queryDate(w, r, "date")
	if !ok {
		return models.Date{}, false
	}
	if d != nil {
		return *d, true
	}
	return today(w, r)
}

func pagination(r *http.Request, defaultLimit int) (page, limit int) {
	page, _ = strconv.Atoi(r.URL.Query().Get("page"))
	limit, _ = strconv.Atoi(r.URL.Query().Get("limit"))
	if page < 1 {
		page = 1
	}
	if limit < 1 {
		limit = defaultLimit
	}
	if limit > 100 {
		limit = 100
	}
	return page, limit
}

package handlers

import (
	"context"
	"net/http"

	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

type StatsHandler struct{ svc *services.StatsService }

func (h *StatsHandler) Overview(w http.ResponseWriter, r *http.Request) {
	t, ok := today(w, r)
	if !ok {
		return
	}
	period := r.URL.Query().Get("period")
	if period == "" {
		period = services.PeriodAll
	}
	out, err := h.svc.Overview(r.Context(), userID(r), t, period)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, out)
}

func (h *StatsHandler) Weekly(w http.ResponseWriter, r *http.Request)  { h.series(w, r, h.svc.Weekly) }
func (h *StatsHandler) Monthly(w http.ResponseWriter, r *http.Request) { h.series(w, r, h.svc.Monthly) }
func (h *StatsHandler) Yearly(w http.ResponseWriter, r *http.Request)  { h.series(w, r, h.svc.Yearly) }

func (h *StatsHandler) series(w http.ResponseWriter, r *http.Request,
	fn func(context.Context, string, models.Date) (*services.Series, error)) {
	d, ok := dateOrToday(w, r)
	if !ok {
		return
	}
	out, err := fn(r.Context(), userID(r), d)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, out)
}

func (h *StatsHandler) Day(w http.ResponseWriter, r *http.Request) {
	d, ok := dateOrToday(w, r)
	if !ok {
		return
	}
	out, err := h.svc.Day(r.Context(), userID(r), d)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, out)
}

func (h *StatsHandler) Week(w http.ResponseWriter, r *http.Request) {
	d, ok := dateOrToday(w, r)
	if !ok {
		return
	}
	t, ok := today(w, r)
	if !ok {
		return
	}
	out, err := h.svc.Week(r.Context(), userID(r), d, t)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, out)
}

// Meta returns the catalogues the UI renders (activity types, reasons), so
// new types only need adding on the server.
func Meta(w http.ResponseWriter, r *http.Request) {
	httpx.Data(w, http.StatusOK, map[string]any{
		"activityTypes":     models.ActivityTypes,
		"completionReasons": models.CompletionReasons,
	})
}

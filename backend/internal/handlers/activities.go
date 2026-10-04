package handlers

import (
	"net/http"

	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

type ActivityHandler struct{ svc *services.ActivityService }

func (h *ActivityHandler) List(w http.ResponseWriter, r *http.Request) {
	from, ok := queryDate(w, r, "from")
	if !ok {
		return
	}
	to, ok := queryDate(w, r, "to")
	if !ok {
		return
	}
	activityType := r.URL.Query().Get("type")
	if activityType != "" && !models.IsValidActivityType(activityType) {
		httpx.FieldError(w, "type", "Unknown activity type.")
		return
	}
	page, limit := pagination(r, 20)
	items, p, err := h.svc.List(r.Context(), userID(r), services.ListActivitiesParams{
		From: from, To: to, ActivityType: activityType, Page: page, Limit: limit,
	})
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.List(w, items, p)
}

func (h *ActivityHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req services.ActivityRequest
	if !decode(w, r, &req) {
		return
	}
	a, err := h.svc.Create(r.Context(), userID(r), req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusCreated, a)
}

func (h *ActivityHandler) Get(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	a, err := h.svc.Get(r.Context(), userID(r), id)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, a)
}

func (h *ActivityHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.ActivityRequest
	if !decode(w, r, &req) {
		return
	}
	a, err := h.svc.Update(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, a)
}

func (h *ActivityHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if err := h.svc.Delete(r.Context(), userID(r), id); err != nil {
		fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

package handlers

import (
	"context"
	"net/http"

	"github.com/vedant-workspaces/pacebook/backend/internal/httpx"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

type TrainingHandler struct{ svc *services.TrainingService }

func (h *TrainingHandler) ListBlocks(w http.ResponseWriter, r *http.Request) {
	t, ok := today(w, r)
	if !ok {
		return
	}
	page, limit := pagination(r, 20)
	items, p, err := h.svc.ListBlocks(r.Context(), userID(r), t, page, limit)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.List(w, items, p)
}

func (h *TrainingHandler) CreateBlock(w http.ResponseWriter, r *http.Request) {
	var req services.BlockRequest
	if !decode(w, r, &req) {
		return
	}
	b, err := h.svc.CreateBlock(r.Context(), userID(r), req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusCreated, b)
}

func (h *TrainingHandler) GetBlock(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	b, err := h.svc.GetBlock(r.Context(), userID(r), id)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, b)
}

func (h *TrainingHandler) UpdateBlock(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.BlockRequest
	if !decode(w, r, &req) {
		return
	}
	b, err := h.svc.UpdateBlock(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, b)
}

func (h *TrainingHandler) DeleteBlock(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if err := h.svc.DeleteBlock(r.Context(), userID(r), id); err != nil {
		fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *TrainingHandler) BlockStats(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	t, ok := today(w, r)
	if !ok {
		return
	}
	stats, err := h.svc.BlockStats(r.Context(), userID(r), id, t)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, stats)
}

func (h *TrainingHandler) ListPlanned(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	from, ok := queryDate(w, r, "from")
	if !ok {
		return
	}
	to, ok := queryDate(w, r, "to")
	if !ok {
		return
	}
	items, err := h.svc.ListPlanned(r.Context(), userID(r), id, from, to)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, items)
}

func (h *TrainingHandler) CreatePlanned(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.PlannedRequest
	if !decode(w, r, &req) {
		return
	}
	t, err := h.svc.CreatePlanned(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusCreated, t)
}

func (h *TrainingHandler) GetPlanned(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	t, err := h.svc.GetPlanned(r.Context(), userID(r), id)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, t)
}

func (h *TrainingHandler) UpdatePlanned(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.PlannedRequest
	if !decode(w, r, &req) {
		return
	}
	t, err := h.svc.UpdatePlanned(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, t)
}

func (h *TrainingHandler) DeletePlanned(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	if err := h.svc.DeletePlanned(r.Context(), userID(r), id); err != nil {
		fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *TrainingHandler) Complete(w http.ResponseWriter, r *http.Request) {
	h.result(w, r, h.svc.Complete)
}

func (h *TrainingHandler) Modify(w http.ResponseWriter, r *http.Request) {
	h.result(w, r, h.svc.Modify)
}

func (h *TrainingHandler) result(w http.ResponseWriter, r *http.Request,
	fn func(context.Context, string, string, services.CompleteRequest) (*models.TrainingActivity, error)) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.CompleteRequest
	if !decode(w, r, &req) {
		return
	}
	t, err := fn(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, t)
}

func (h *TrainingHandler) Skip(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	var req services.SkipRequest
	if !decode(w, r, &req) {
		return
	}
	t, err := h.svc.Skip(r.Context(), userID(r), id, req)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, t)
}

func (h *TrainingHandler) Reset(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r)
	if !ok {
		return
	}
	t, err := h.svc.Reset(r.Context(), userID(r), id)
	if err != nil {
		fail(w, r, err)
		return
	}
	httpx.Data(w, http.StatusOK, t)
}

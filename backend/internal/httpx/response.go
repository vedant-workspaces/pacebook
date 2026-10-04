// Package httpx contains the JSON response envelope shared by handlers and
// middleware:
//
//	{"data": ...}                       success
//	{"data": [...], "pagination": {...}} paginated list
//	{"error": {"code": ..., "message": ...}}
package httpx

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const (
	CodeValidation   = "VALIDATION_ERROR"
	CodeUnauthorized = "UNAUTHORIZED"
	CodeForbidden    = "FORBIDDEN"
	CodeNotFound     = "NOT_FOUND"
	CodeConflict     = "CONFLICT"
	CodeInternal     = "INTERNAL_ERROR"
)

type errorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Field   string `json:"field,omitempty"`
}

func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("encode response", "err", err)
	}
}

func Data(w http.ResponseWriter, status int, data any) {
	JSON(w, status, map[string]any{"data": data})
}

func List(w http.ResponseWriter, data any, p models.Pagination) {
	JSON(w, http.StatusOK, map[string]any{"data": data, "pagination": p})
}

func Error(w http.ResponseWriter, status int, code, message string) {
	JSON(w, status, map[string]any{"error": errorBody{Code: code, Message: message}})
}

func FieldError(w http.ResponseWriter, field, message string) {
	JSON(w, http.StatusBadRequest, map[string]any{"error": errorBody{Code: CodeValidation, Message: message, Field: field}})
}

func Internal(w http.ResponseWriter, r *http.Request, err error) {
	// Log the real cause; the client only ever sees a generic message.
	slog.Error("internal error", "method", r.Method, "path", r.URL.Path, "err", err)
	Error(w, http.StatusInternalServerError, CodeInternal, "Something went wrong. Please try again.")
}

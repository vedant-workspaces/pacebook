// Package services holds business rules and validation. Handlers translate
// these errors into HTTP responses; internal errors are never exposed.
package services

import (
	"errors"
	"fmt"

	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

// ErrNotFound covers both missing rows and rows owned by another user.
var ErrNotFound = repository.ErrNotFound

type ValidationError struct {
	Field   string
	Message string
}

func (e *ValidationError) Error() string { return e.Message }

func invalid(field, format string, args ...any) error {
	return &ValidationError{Field: field, Message: fmt.Sprintf(format, args...)}
}

type ConflictError struct{ Message string }

func (e *ConflictError) Error() string { return e.Message }

func IsValidation(err error) (*ValidationError, bool) {
	var v *ValidationError
	ok := errors.As(err, &v)
	return v, ok
}

func IsConflict(err error) (*ConflictError, bool) {
	var c *ConflictError
	ok := errors.As(err, &c)
	return c, ok
}

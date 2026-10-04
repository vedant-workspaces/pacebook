package services

import (
	"math"
	"strings"
	"unicode/utf8"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const (
	MaxNameLength        = 120
	MaxGoalLength        = 200
	MaxDescriptionLength = 2000
	MaxCommentLength     = 2000

	MaxDistanceKm       = 500.0 // exclusive
	MinPaceSecondsPerKm = 120   // 2:00/km
	MaxPaceSecondsPerKm = 1800  // 30:00/km
	MinHeartRate        = 30
	MaxHeartRate        = 250
	MaxDurationSeconds  = 48 * 3600
	MaxBlockLengthDays  = 731
)

// cleanText trims s and returns nil for empty strings so optional text
// columns store NULL rather than "".
func cleanText(s *string) *string {
	if s == nil {
		return nil
	}
	t := strings.TrimSpace(*s)
	if t == "" {
		return nil
	}
	return &t
}

func checkLength(field string, s *string, max int) error {
	if s != nil && utf8.RuneCountInString(*s) > max {
		return invalid(field, "%s must be at most %d characters", fieldLabel(field), max)
	}
	return nil
}

func requireText(field string, s string, max int) (string, error) {
	t := strings.TrimSpace(s)
	if t == "" {
		return "", invalid(field, "%s is required", fieldLabel(field))
	}
	if utf8.RuneCountInString(t) > max {
		return "", invalid(field, "%s must be at most %d characters", fieldLabel(field), max)
	}
	return t, nil
}

func fieldLabel(field string) string {
	switch field {
	case "name":
		return "Name"
	case "title":
		return "Title"
	case "goal":
		return "Goal"
	case "description":
		return "Description"
	case "comment":
		return "Comment"
	case "notes":
		return "Notes"
	}
	return field
}

func validateDate(field string, d models.Date) error {
	if d.IsZero() {
		return invalid(field, "A valid date is required")
	}
	if d.Time().Year() < 1900 || d.Time().Year() > 2200 {
		return invalid(field, "Date is out of range")
	}
	return nil
}

func validateActivityType(field, t string) error {
	if !models.IsValidActivityType(t) {
		return invalid(field, "Unknown activity type")
	}
	return nil
}

// ValidateActualDistance: an actual distance, when given, is > 0 and < 500 km.
func ValidateActualDistance(d *float64) error {
	if d == nil {
		return nil
	}
	if math.IsNaN(*d) || *d <= 0 {
		return invalid("distanceKm", "Distance must be greater than zero")
	}
	if *d >= MaxDistanceKm {
		return invalid("distanceKm", "Distance must be less than 500 km")
	}
	return nil
}

// ValidatePlannedDistance: a planned distance, when given, is >= 0 and < 500 km.
func ValidatePlannedDistance(d *float64) error {
	if d == nil {
		return nil
	}
	if math.IsNaN(*d) || *d < 0 {
		return invalid("plannedDistanceKm", "Planned distance cannot be negative")
	}
	if *d >= MaxDistanceKm {
		return invalid("plannedDistanceKm", "Planned distance must be less than 500 km")
	}
	return nil
}

// ValidatePace accepts paces between 2:00/km and 30:00/km.
func ValidatePace(field string, p *int) error {
	if p == nil {
		return nil
	}
	if *p < MinPaceSecondsPerKm || *p > MaxPaceSecondsPerKm {
		return invalid(field, "Pace must be between 2:00 and 30:00 per km")
	}
	return nil
}

func ValidateHeartRate(hr *int) error {
	if hr == nil {
		return nil
	}
	if *hr < MinHeartRate || *hr > MaxHeartRate {
		return invalid("averageHeartRate", "Heart rate must be between 30 and 250 bpm")
	}
	return nil
}

func validateDuration(d *int) error {
	if d == nil {
		return nil
	}
	if *d <= 0 || *d > MaxDurationSeconds {
		return invalid("plannedDurationSeconds", "Planned duration must be between 1 second and 48 hours")
	}
	return nil
}

// roundDistance keeps distances at the 3-decimal precision stored in the DB.
func roundDistance(d *float64) *float64 {
	if d == nil {
		return nil
	}
	v := math.Round(*d*1000) / 1000
	return &v
}

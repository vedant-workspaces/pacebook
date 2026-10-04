package services

import (
	"testing"

	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

func ptr[T any](v T) *T { return &v }

func TestValidateActualDistance(t *testing.T) {
	for _, ok := range []float64{0.1, 8.4, 499.9} {
		if err := ValidateActualDistance(ptr(ok)); err != nil {
			t.Errorf("%v: unexpected error %v", ok, err)
		}
	}
	for _, bad := range []float64{0, -1, 500, 1000} {
		if err := ValidateActualDistance(ptr(bad)); err == nil {
			t.Errorf("%v: expected error", bad)
		}
	}
	if err := ValidateActualDistance(nil); err != nil {
		t.Errorf("nil distance should be allowed: %v", err)
	}
}

func TestValidatePace(t *testing.T) {
	// 3:30, 4:45, 5:30, 6:15, 10:00 per km.
	for _, ok := range []int{210, 285, 330, 375, 600} {
		if err := ValidatePace("pace", ptr(ok)); err != nil {
			t.Errorf("%d: unexpected error %v", ok, err)
		}
	}
	for _, bad := range []int{0, 60, 119, 1801} {
		if err := ValidatePace("pace", ptr(bad)); err == nil {
			t.Errorf("%d: expected error", bad)
		}
	}
}

func TestValidateHeartRate(t *testing.T) {
	for _, ok := range []int{30, 151, 250} {
		if err := ValidateHeartRate(ptr(ok)); err != nil {
			t.Errorf("%d: unexpected error %v", ok, err)
		}
	}
	for _, bad := range []int{29, 251} {
		if err := ValidateHeartRate(ptr(bad)); err == nil {
			t.Errorf("%d: expected error", bad)
		}
	}
}

func TestValidatePlannedDistanceAllowsZero(t *testing.T) {
	if err := ValidatePlannedDistance(ptr(0.0)); err != nil {
		t.Errorf("zero planned distance should be allowed: %v", err)
	}
	if err := ValidatePlannedDistance(ptr(-0.5)); err == nil {
		t.Error("negative planned distance should be rejected")
	}
}

func TestWeightedPace(t *testing.T) {
	// 5 km @ 5:00 + 20 km @ 6:00 → 8700 s / 25 km = 348 s/km (5:48), not 5:30.
	got := repository.WeightedPace(5*300+20*360, 25)
	if got == nil || *got != 348 {
		t.Fatalf("weighted pace = %v, want 348", got)
	}
	if repository.WeightedPace(0, 0) != nil {
		t.Fatal("expected nil pace without distance")
	}
}

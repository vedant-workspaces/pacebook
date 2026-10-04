package models

import (
	"encoding/json"
	"testing"
)

func TestWeekStartIsMonday(t *testing.T) {
	cases := map[string]string{
		"2026-09-28": "2026-09-28", // Monday
		"2026-10-01": "2026-09-28", // Thursday
		"2026-10-04": "2026-09-28", // Sunday belongs to the preceding Monday
		"2026-10-05": "2026-10-05",
		"2027-01-01": "2026-12-28", // across a year boundary
	}
	for in, want := range cases {
		d, err := ParseDate(in)
		if err != nil {
			t.Fatal(err)
		}
		if got := d.WeekStart().String(); got != want {
			t.Errorf("WeekStart(%s) = %s, want %s", in, got, want)
		}
	}
}

func TestDateJSONHasNoTimezoneShift(t *testing.T) {
	var v struct {
		D Date `json:"d"`
	}
	if err := json.Unmarshal([]byte(`{"d":"2026-10-04"}`), &v); err != nil {
		t.Fatal(err)
	}
	b, _ := json.Marshal(v)
	if string(b) != `{"d":"2026-10-04"}` {
		t.Fatalf("round trip = %s", b)
	}
	if err := json.Unmarshal([]byte(`{"d":"2026-13-01"}`), &v); err == nil {
		t.Fatal("expected error for invalid month")
	}
}

package models

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
)

const DateLayout = "2006-01-02"

// Date is a calendar date with no time-of-day or timezone. It maps to a
// PostgreSQL DATE and to "YYYY-MM-DD" in JSON so a run logged on the user's
// local Sunday never shifts to Saturday through UTC conversion.
type Date struct {
	t time.Time // always midnight UTC
}

func NewDate(year int, month time.Month, day int) Date {
	return Date{time.Date(year, month, day, 0, 0, 0, 0, time.UTC)}
}

func DateOf(t time.Time) Date { return NewDate(t.Year(), t.Month(), t.Day()) }

func ParseDate(s string) (Date, error) {
	t, err := time.Parse(DateLayout, s)
	if err != nil {
		return Date{}, fmt.Errorf("invalid date %q, expected YYYY-MM-DD", s)
	}
	return Date{t}, nil
}

func (d Date) IsZero() bool          { return d.t.IsZero() }
func (d Date) Time() time.Time       { return d.t }
func (d Date) String() string        { return d.t.Format(DateLayout) }
func (d Date) AddDays(n int) Date    { return Date{d.t.AddDate(0, 0, n)} }
func (d Date) AddMonths(n int) Date  { return Date{d.t.AddDate(0, n, 0)} }
func (d Date) Before(o Date) bool    { return d.t.Before(o.t) }
func (d Date) After(o Date) bool     { return d.t.After(o.t) }
func (d Date) Weekday() time.Weekday { return d.t.Weekday() }
func (d Date) DaysUntil(o Date) int  { return int(o.t.Sub(d.t).Hours() / 24) }
func (d Date) MonthStart() Date      { return NewDate(d.t.Year(), d.t.Month(), 1) }
func (d Date) YearStart() Date       { return NewDate(d.t.Year(), time.January, 1) }

// WeekStart returns the Monday of the Monday→Sunday week containing d.
func (d Date) WeekStart() Date {
	offset := (int(d.t.Weekday()) + 6) % 7
	return d.AddDays(-offset)
}

func (d Date) MarshalJSON() ([]byte, error) {
	if d.IsZero() {
		return []byte("null"), nil
	}
	return json.Marshal(d.String())
}

func (d *Date) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return fmt.Errorf("date must be a string")
	}
	if s == "" {
		*d = Date{}
		return nil
	}
	parsed, err := ParseDate(s)
	if err != nil {
		return err
	}
	*d = parsed
	return nil
}

// ScanDate implements pgtype.DateScanner.
func (d *Date) ScanDate(v pgtype.Date) error {
	if !v.Valid {
		*d = Date{}
		return nil
	}
	*d = DateOf(v.Time)
	return nil
}

// DateValue implements pgtype.DateValuer.
func (d Date) DateValue() (pgtype.Date, error) {
	if d.IsZero() {
		return pgtype.Date{}, nil
	}
	return pgtype.Date{Time: d.t, Valid: true}, nil
}

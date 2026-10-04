package services

import (
	"context"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

// StatsService computes mileage and training statistics. "today" is always
// the user's local date, supplied by the client, so weeks/months/years line
// up with the runner's calendar rather than the server's timezone. Weeks run
// Monday → Sunday everywhere.
type StatsService struct{ store *repository.Store }

func NewStatsService(store *repository.Store) *StatsService { return &StatsService{store} }

type PlannedVsActual struct {
	PlannedDistanceKm float64  `json:"plannedDistanceKm"`
	ActualDistanceKm  float64  `json:"actualDistanceKm"`
	DifferenceKm      float64  `json:"differenceKm"`
	CompletionPct     *float64 `json:"completionPct"`
}

func comparison(p models.PlanSummary) PlannedVsActual {
	return PlannedVsActual{
		PlannedDistanceKm: p.PlannedDistanceKm,
		ActualDistanceKm:  p.ActualDistanceKm,
		DifferenceKm:      p.ActualDistanceKm - p.PlannedDistanceKm,
		CompletionPct:     p.CompletionPct,
	}
}

type Range struct {
	From models.Date `json:"from"`
	To   models.Date `json:"to"` // inclusive
}

// Period names accepted by the overview endpoint.
const (
	PeriodWeek  = "week"
	PeriodMonth = "month"
	PeriodYear  = "year"
	PeriodAll   = "all"
)

// periodBounds returns the half-open [from, to) range of a period containing
// today. Both are nil for "all".
func periodBounds(period string, today models.Date) (*models.Date, *models.Date, bool) {
	var from, to models.Date
	switch period {
	case PeriodWeek:
		from = today.WeekStart()
		to = from.AddDays(7)
	case PeriodMonth:
		from = today.MonthStart()
		to = from.AddMonths(1)
	case PeriodYear:
		from = today.YearStart()
		to = from.AddMonths(12)
	case PeriodAll:
		return nil, nil, true
	default:
		return nil, nil, false
	}
	return &from, &to, true
}

type Overview struct {
	Today           models.Date             `json:"today"`
	Period          string                  `json:"period"`
	Totals          repository.PeriodTotals `json:"totals"`
	Aggregates      repository.Aggregates   `json:"aggregates"`
	LongestActivity *models.Activity        `json:"longestActivity"`
	FastestActivity *models.Activity        `json:"fastestActivity"`
	Training        models.PlanSummary      `json:"training"`
	PlannedVsActual PlannedVsActual         `json:"plannedVsActual"`
}

// Overview returns week/month/year/all-time totals plus aggregates, records
// and plan comparison for the selected period.
func (s *StatsService) Overview(ctx context.Context, userID string, today models.Date, period string) (*Overview, error) {
	from, to, ok := periodBounds(period, today)
	if !ok {
		return nil, invalid("period", "Period must be week, month, year or all")
	}
	out := &Overview{Today: today, Period: period}
	var err error
	if out.Totals, err = s.store.PeriodTotals(ctx, userID, today.WeekStart(), today.MonthStart(), today.YearStart(), today.AddDays(1)); err != nil {
		return nil, err
	}
	if out.Aggregates, err = s.store.Aggregates(ctx, userID, from, to); err != nil {
		return nil, err
	}
	if out.LongestActivity, err = s.store.LongestActivity(ctx, userID, from, to); err != nil {
		return nil, err
	}
	if out.FastestActivity, err = s.store.FastestActivity(ctx, userID, from, to); err != nil {
		return nil, err
	}
	// Weeks compare against the whole week's plan; longer periods only
	// against the plan up to today, so future sessions don't inflate it.
	planTo := to
	if period != PeriodWeek {
		end := today.AddDays(1)
		if to == nil || end.Before(*to) {
			planTo = &end
		}
	}
	if out.Training, err = s.store.PlanSummary(ctx, userID, "", from, planTo, today); err != nil {
		return nil, err
	}
	out.PlannedVsActual = comparison(out.Training)
	return out, nil
}

type Series struct {
	Range      Range                    `json:"range"`
	Bucket     string                   `json:"bucket"`
	Points     []repository.SeriesPoint `json:"points"`
	TotalKm    float64                  `json:"totalDistanceKm"`
	TotalCount int                      `json:"activityCount"`
	PlannedKm  float64                  `json:"plannedDistanceKm"`
}

func (s *StatsService) series(ctx context.Context, userID string, from, to models.Date, bucket string) (*Series, error) {
	points, err := s.store.MileageSeries(ctx, userID, from, to, bucket)
	if err != nil {
		return nil, err
	}
	out := &Series{Range: Range{From: from, To: to.AddDays(-1)}, Bucket: bucket, Points: points}
	for _, p := range points {
		out.TotalKm += p.DistanceKm
		out.TotalCount += p.ActivityCount
		out.PlannedKm += p.PlannedDistanceKm
	}
	return out, nil
}

// Weekly: daily actual mileage for the Monday→Sunday week containing date.
func (s *StatsService) Weekly(ctx context.Context, userID string, date models.Date) (*Series, error) {
	from := date.WeekStart()
	return s.series(ctx, userID, from, from.AddDays(7), repository.BucketDay)
}

// Monthly: daily actual mileage for the calendar month containing date.
func (s *StatsService) Monthly(ctx context.Context, userID string, date models.Date) (*Series, error) {
	from := date.MonthStart()
	return s.series(ctx, userID, from, from.AddMonths(1), repository.BucketDay)
}

// Yearly: monthly actual mileage for the calendar year containing date.
func (s *StatsService) Yearly(ctx context.Context, userID string, date models.Date) (*Series, error) {
	from := date.YearStart()
	return s.series(ctx, userID, from, from.AddMonths(12), repository.BucketMonth)
}

type Day struct {
	Date       models.Date               `json:"date"`
	Planned    []models.TrainingActivity `json:"planned"`
	Actual     []models.Activity         `json:"actual"`
	DistanceKm float64                   `json:"distanceKm"`
	PlannedKm  float64                   `json:"plannedDistanceKm"`
}

// Day returns what was planned and what was actually done on one date.
func (s *StatsService) Day(ctx context.Context, userID string, date models.Date) (*Day, error) {
	planned, err := s.store.ListPlanned(ctx, userID, repository.PlannedFilter{From: &date, To: &date})
	if err != nil {
		return nil, err
	}
	actual, _, err := s.store.ListActivities(ctx, userID, repository.ActivityFilter{From: &date, To: &date, Limit: 500})
	if err != nil {
		return nil, err
	}
	// Chronological within the day (the list endpoint is newest-first).
	for i, j := 0, len(actual)-1; i < j; i, j = i+1, j-1 {
		actual[i], actual[j] = actual[j], actual[i]
	}
	out := &Day{Date: date, Planned: planned, Actual: actual}
	for _, a := range actual {
		if a.DistanceKm != nil {
			out.DistanceKm += *a.DistanceKm
		}
	}
	for _, p := range planned {
		if p.PlannedDistanceKm != nil && p.Status != models.StatusSkipped {
			out.PlannedKm += *p.PlannedDistanceKm
		}
	}
	return out, nil
}

type Week struct {
	Range           Range                     `json:"range"`
	Days            []repository.SeriesPoint  `json:"days"`
	Planned         []models.TrainingActivity `json:"planned"`
	Training        models.PlanSummary        `json:"training"`
	PlannedVsActual PlannedVsActual           `json:"plannedVsActual"`
}

// Week summarises the Monday→Sunday week containing date: daily mileage,
// the planned sessions and the planned-vs-actual comparison.
func (s *StatsService) Week(ctx context.Context, userID string, date, today models.Date) (*Week, error) {
	from := date.WeekStart()
	to := from.AddDays(7)
	last := to.AddDays(-1)
	days, err := s.store.MileageSeries(ctx, userID, from, to, repository.BucketDay)
	if err != nil {
		return nil, err
	}
	planned, err := s.store.ListPlanned(ctx, userID, repository.PlannedFilter{From: &from, To: &last})
	if err != nil {
		return nil, err
	}
	summary, err := s.store.PlanSummary(ctx, userID, "", &from, &to, today)
	if err != nil {
		return nil, err
	}
	return &Week{
		Range:           Range{From: from, To: last},
		Days:            days,
		Planned:         planned,
		Training:        summary,
		PlannedVsActual: comparison(summary),
	}, nil
}

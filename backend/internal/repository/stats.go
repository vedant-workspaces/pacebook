package repository

import (
	"context"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

// All statistics are computed with SQL aggregation; complete histories are
// never loaded into memory. Ranges are half-open: [from, to).

type Totals struct {
	DistanceKm    float64 `json:"distanceKm"`
	ActivityCount int     `json:"activityCount"`
}

type PeriodTotals struct {
	Week  Totals `json:"week"`
	Month Totals `json:"month"`
	Year  Totals `json:"year"`
	All   Totals `json:"allTime"`
}

// PeriodTotals returns actual mileage and activity counts for the week, month
// and year containing today, and for all time, in one scan.
func (s *Store) PeriodTotals(ctx context.Context, userID string, weekFrom, monthFrom, yearFrom, to models.Date) (PeriodTotals, error) {
	var p PeriodTotals
	err := s.db.QueryRow(ctx, `
		SELECT
		  COALESCE(SUM(distance_km) FILTER (WHERE activity_date >= $2 AND activity_date < $5), 0)::float8,
		  COUNT(*)                  FILTER (WHERE activity_date >= $2 AND activity_date < $5),
		  COALESCE(SUM(distance_km) FILTER (WHERE activity_date >= $3 AND activity_date < $5), 0)::float8,
		  COUNT(*)                  FILTER (WHERE activity_date >= $3 AND activity_date < $5),
		  COALESCE(SUM(distance_km) FILTER (WHERE activity_date >= $4 AND activity_date < $5), 0)::float8,
		  COUNT(*)                  FILTER (WHERE activity_date >= $4 AND activity_date < $5),
		  COALESCE(SUM(distance_km), 0)::float8,
		  COUNT(*)
		FROM activities
		WHERE user_id = $1`, userID, weekFrom, monthFrom, yearFrom, to,
	).Scan(&p.Week.DistanceKm, &p.Week.ActivityCount, &p.Month.DistanceKm, &p.Month.ActivityCount,
		&p.Year.DistanceKm, &p.Year.ActivityCount, &p.All.DistanceKm, &p.All.ActivityCount)
	return p, err
}

type Aggregates struct {
	DistanceKm    float64  `json:"distanceKm"`
	ActivityCount int      `json:"activityCount"`
	AvgDistanceKm *float64 `json:"averageDistanceKm"`
	// Weighted: total time / total distance over activities with a pace.
	AvgPaceSecPerKm  *float64 `json:"averagePaceSecondsPerKm"`
	AvgHeartRate     *float64 `json:"averageHeartRate"`
	TotalTimeSeconds float64  `json:"totalTimeSeconds"`
}

// Aggregates computes activity aggregates over [from, to); nil bounds are open.
func (s *Store) Aggregates(ctx context.Context, userID string, from, to *models.Date) (Aggregates, error) {
	var a Aggregates
	var pacedDistance float64
	err := s.db.QueryRow(ctx, `
		SELECT COALESCE(SUM(distance_km), 0)::float8,
		       COUNT(*),
		       AVG(distance_km)::float8,
		       COALESCE(SUM(distance_km * pace_seconds_per_km)
		                FILTER (WHERE distance_km IS NOT NULL AND pace_seconds_per_km IS NOT NULL), 0)::float8,
		       COALESCE(SUM(distance_km)
		                FILTER (WHERE distance_km IS NOT NULL AND pace_seconds_per_km IS NOT NULL), 0)::float8,
		       AVG(average_heart_rate)::float8
		  FROM activities
		 WHERE user_id = $1
		   AND ($2::date IS NULL OR activity_date >= $2)
		   AND ($3::date IS NULL OR activity_date < $3)`, userID, from, to,
	).Scan(&a.DistanceKm, &a.ActivityCount, &a.AvgDistanceKm, &a.TotalTimeSeconds, &pacedDistance, &a.AvgHeartRate)
	if err != nil {
		return a, err
	}
	a.AvgPaceSecPerKm = WeightedPace(a.TotalTimeSeconds, pacedDistance)
	return a, nil
}

// WeightedPace returns total time / total distance, or nil with no distance.
func WeightedPace(totalSeconds, distanceKm float64) *float64 {
	if distanceKm <= 0 {
		return nil
	}
	p := totalSeconds / distanceKm
	return &p
}

// LongestActivity returns the activity with the greatest distance in range.
func (s *Store) LongestActivity(ctx context.Context, userID string, from, to *models.Date) (*models.Activity, error) {
	return s.oneActivity(ctx, `AND a.distance_km IS NOT NULL ORDER BY a.distance_km DESC, a.activity_date DESC`, userID, from, to)
}

// FastestActivity returns the activity with the quickest average pace among
// those of at least 1 km (shorter efforts are too noisy to be meaningful).
func (s *Store) FastestActivity(ctx context.Context, userID string, from, to *models.Date) (*models.Activity, error) {
	return s.oneActivity(ctx, `AND a.pace_seconds_per_km IS NOT NULL AND a.distance_km >= 1
		ORDER BY a.pace_seconds_per_km ASC, a.activity_date DESC`, userID, from, to)
}

func (s *Store) oneActivity(ctx context.Context, tail, userID string, from, to *models.Date) (*models.Activity, error) {
	var a models.Activity
	err := s.db.QueryRow(ctx, `SELECT `+activityColumns+` FROM activities a
		 WHERE a.user_id = $1
		   AND ($2::date IS NULL OR a.activity_date >= $2)
		   AND ($3::date IS NULL OR a.activity_date < $3) `+tail+` LIMIT 1`, userID, from, to).
		Scan(activityDest(&a)...)
	if err != nil {
		if notFound(err) == ErrNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &a, nil
}

// PlanSummary aggregates planned activities over [from, to) (nil = open) and
// optionally a single block, plus actual mileage over the same dates.
func (s *Store) PlanSummary(ctx context.Context, userID string, blockID string, from, to *models.Date, today models.Date) (models.PlanSummary, error) {
	var p models.PlanSummary
	err := s.db.QueryRow(ctx, `
		SELECT `+planSummarySelect("$5")+`,
		       COALESCE((SELECT SUM(a.distance_km) FROM activities a
		                  WHERE a.user_id = $1
		                    AND ($3::date IS NULL OR a.activity_date >= $3)
		                    AND ($4::date IS NULL OR a.activity_date < $4)), 0)::float8
		  FROM training_activities ta
		 WHERE ta.user_id = $1
		   AND ($2::uuid IS NULL OR ta.training_block_id = $2::uuid)
		   AND ($3::date IS NULL OR ta.activity_date >= $3)
		   AND ($4::date IS NULL OR ta.activity_date < $4)`,
		userID, nullIfEmpty(blockID), from, to, today,
	).Scan(append(summaryDest(&p), &p.ActualDistanceKm)...)
	FinalizeSummary(&p)
	return p, err
}

type SeriesPoint struct {
	Date              models.Date `json:"date"`
	DistanceKm        float64     `json:"distanceKm"`
	ActivityCount     int         `json:"activityCount"`
	PlannedDistanceKm float64     `json:"plannedDistanceKm"`
}

// Bucket sizes for MileageSeries.
const (
	BucketDay   = "day"
	BucketWeek  = "week"
	BucketMonth = "month"
)

// MileageSeries returns actual and planned (non-skipped) distance per bucket
// over [from, to). from must be aligned to the bucket start. Buckets with no
// activity are included with zeros. date_trunc('week') is ISO, i.e. Monday.
func (s *Store) MileageSeries(ctx context.Context, userID string, from, to models.Date, bucket string) ([]SeriesPoint, error) {
	step := map[string]string{BucketDay: "1 day", BucketWeek: "7 days", BucketMonth: "1 month"}[bucket]
	if step == "" {
		panic("invalid bucket " + bucket)
	}
	rows, err := s.db.Query(ctx, `
		WITH buckets AS (
		  SELECT generate_series($2::date, $3::date - 1, $5::interval)::date AS bucket
		),
		actual AS (
		  SELECT date_trunc($4, activity_date)::date AS bucket,
		         SUM(distance_km)::float8 AS km, COUNT(*) AS n
		    FROM activities
		   WHERE user_id = $1 AND activity_date >= $2 AND activity_date < $3
		   GROUP BY 1
		),
		planned AS (
		  SELECT date_trunc($4, activity_date)::date AS bucket,
		         SUM(planned_distance_km)::float8 AS km
		    FROM training_activities
		   WHERE user_id = $1 AND activity_date >= $2 AND activity_date < $3 AND status <> 'skipped'
		   GROUP BY 1
		)
		SELECT b.bucket, COALESCE(a.km, 0), COALESCE(a.n, 0), COALESCE(p.km, 0)
		  FROM buckets b
		  LEFT JOIN actual a ON a.bucket = b.bucket
		  LEFT JOIN planned p ON p.bucket = b.bucket
		 ORDER BY b.bucket`, userID, from, to, bucket, step)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []SeriesPoint{}
	for rows.Next() {
		var p SeriesPoint
		if err := rows.Scan(&p.Date, &p.DistanceKm, &p.ActivityCount, &p.PlannedDistanceKm); err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

type BlockWeek struct {
	WeekStart         models.Date `json:"weekStart"`
	PlannedDistanceKm float64     `json:"plannedDistanceKm"`
	ActualDistanceKm  float64     `json:"actualDistanceKm"`
	Completed         int         `json:"completed"`
	Due               int         `json:"due"`
}

// BlockWeeks compares planned and actual distance for every Monday-based week
// of a block. Actual distance only counts days inside the block's dates.
func (s *Store) BlockWeeks(ctx context.Context, userID string, b *models.TrainingBlock, today models.Date) ([]BlockWeek, error) {
	rows, err := s.db.Query(ctx, `
		WITH weeks AS (
		  SELECT generate_series($3::date, $4::date, interval '7 days')::date AS wk
		),
		planned AS (
		  SELECT date_trunc('week', activity_date)::date AS wk,
		         COALESCE(SUM(planned_distance_km) FILTER (WHERE status <> 'skipped'), 0)::float8 AS km,
		         COUNT(*) FILTER (WHERE activity_type <> 'rest' AND status IN ('completed', 'modified')) AS done,
		         COUNT(*) FILTER (WHERE activity_type <> 'rest' AND (status <> 'planned' OR activity_date <= $6)) AS due
		    FROM training_activities
		   WHERE training_block_id = $1 AND user_id = $2
		   GROUP BY 1
		),
		actual AS (
		  SELECT date_trunc('week', activity_date)::date AS wk, SUM(distance_km)::float8 AS km
		    FROM activities
		   WHERE user_id = $2 AND activity_date >= $5 AND activity_date <= $4
		   GROUP BY 1
		)
		SELECT w.wk, COALESCE(p.km, 0), COALESCE(a.km, 0), COALESCE(p.done, 0), COALESCE(p.due, 0)
		  FROM weeks w
		  LEFT JOIN planned p ON p.wk = w.wk
		  LEFT JOIN actual a ON a.wk = w.wk
		 ORDER BY w.wk`, b.ID, userID, b.StartDate.WeekStart(), b.EndDate, b.StartDate, today)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []BlockWeek{}
	for rows.Next() {
		var w BlockWeek
		if err := rows.Scan(&w.WeekStart, &w.PlannedDistanceKm, &w.ActualDistanceKm, &w.Completed, &w.Due); err != nil {
			return nil, err
		}
		out = append(out, w)
	}
	return out, rows.Err()
}

// AveragePlannedDistance is the mean planned distance of a block's non-rest
// activities that have a planned distance.
func (s *Store) AveragePlannedDistance(ctx context.Context, userID, blockID string) (*float64, error) {
	var v *float64
	err := s.db.QueryRow(ctx, `
		SELECT AVG(planned_distance_km)::float8 FROM training_activities
		 WHERE training_block_id = $1 AND user_id = $2
		   AND activity_type <> 'rest' AND planned_distance_km > 0`, blockID, userID).Scan(&v)
	return v, err
}

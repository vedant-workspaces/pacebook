package repository

import (
	"context"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const activityColumns = `a.id, a.user_id, a.training_activity_id, a.activity_date, a.activity_type, a.title,
	a.distance_km::float8, a.pace_seconds_per_km, a.average_heart_rate, a.comment, a.source, a.created_at, a.updated_at`

func activityDest(a *models.Activity) []any {
	return []any{&a.ID, &a.UserID, &a.TrainingActivityID, &a.ActivityDate, &a.ActivityType, &a.Title,
		&a.DistanceKm, &a.PaceSecondsPerKm, &a.AverageHeartRate, &a.Comment, &a.Source, &a.CreatedAt, &a.UpdatedAt}
}

// nullableActivity scans activityColumns from a LEFT JOIN where the whole
// row may be NULL.
type nullableActivity struct {
	ID, UserID, ActivityType, Title, Source *string
	TrainingActivityID                      *string
	ActivityDate                            models.Date
	DistanceKm                              *float64
	Pace, HR                                *int
	Comment                                 *string
	CreatedAt, UpdatedAt                    *time.Time
}

func (n *nullableActivity) dest() []any {
	return []any{&n.ID, &n.UserID, &n.TrainingActivityID, &n.ActivityDate, &n.ActivityType, &n.Title,
		&n.DistanceKm, &n.Pace, &n.HR, &n.Comment, &n.Source, &n.CreatedAt, &n.UpdatedAt}
}

func (n *nullableActivity) toActivity() *models.Activity {
	if n.ID == nil {
		return nil
	}
	return &models.Activity{
		ID: *n.ID, UserID: *n.UserID, TrainingActivityID: n.TrainingActivityID, ActivityDate: n.ActivityDate,
		ActivityType: *n.ActivityType, Title: *n.Title, DistanceKm: n.DistanceKm, PaceSecondsPerKm: n.Pace,
		AverageHeartRate: n.HR, Comment: n.Comment, Source: *n.Source, CreatedAt: *n.CreatedAt, UpdatedAt: *n.UpdatedAt,
	}
}

type ActivityInput struct {
	ActivityDate     models.Date
	ActivityType     string
	Title            string
	DistanceKm       *float64
	PaceSecondsPerKm *int
	AverageHeartRate *int
	Comment          *string
}

// CreateActivity inserts an actual activity. trainingActivityID must already
// have been verified as owned by userID (the composite foreign key enforces
// it again at the database level).
func (s *Store) CreateActivity(ctx context.Context, userID string, trainingActivityID *string, in ActivityInput) (*models.Activity, error) {
	var a models.Activity
	err := s.db.QueryRow(ctx, `
		INSERT INTO activities AS a
		       (user_id, training_activity_id, activity_date, activity_type, title,
		        distance_km, pace_seconds_per_km, average_heart_rate, comment, source)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING `+activityColumns,
		userID, trainingActivityID, in.ActivityDate, in.ActivityType, in.Title,
		in.DistanceKm, in.PaceSecondsPerKm, in.AverageHeartRate, in.Comment, models.SourceManual,
	).Scan(activityDest(&a)...)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrConflict
		}
		return nil, err
	}
	return &a, nil
}

func (s *Store) GetActivity(ctx context.Context, userID, id string) (*models.Activity, error) {
	var a models.Activity
	err := s.db.QueryRow(ctx, `SELECT `+activityColumns+` FROM activities a WHERE a.id = $1 AND a.user_id = $2`, id, userID).
		Scan(activityDest(&a)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &a, nil
}

func (s *Store) GetActivityByPlanned(ctx context.Context, userID, trainingActivityID string) (*models.Activity, error) {
	var a models.Activity
	err := s.db.QueryRow(ctx, `SELECT `+activityColumns+` FROM activities a
		WHERE a.training_activity_id = $1 AND a.user_id = $2`, trainingActivityID, userID).
		Scan(activityDest(&a)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &a, nil
}

// UpdateActivity changes an activity's details. It never touches
// training_activity_id, so ordinary edits cannot detach a linked activity.
func (s *Store) UpdateActivity(ctx context.Context, userID, id string, in ActivityInput) (*models.Activity, error) {
	var a models.Activity
	err := s.db.QueryRow(ctx, `
		UPDATE activities AS a
		   SET activity_date = $3, activity_type = $4, title = $5, distance_km = $6,
		       pace_seconds_per_km = $7, average_heart_rate = $8, comment = $9, updated_at = now()
		 WHERE a.id = $1 AND a.user_id = $2
		RETURNING `+activityColumns,
		id, userID, in.ActivityDate, in.ActivityType, in.Title, in.DistanceKm,
		in.PaceSecondsPerKm, in.AverageHeartRate, in.Comment,
	).Scan(activityDest(&a)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &a, nil
}

func (s *Store) DeleteActivity(ctx context.Context, userID, id string) error {
	tag, err := s.db.Exec(ctx, `DELETE FROM activities WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

type ActivityFilter struct {
	From         *models.Date
	To           *models.Date // inclusive
	ActivityType string
	Limit        int
	Offset       int
}

func (s *Store) ListActivities(ctx context.Context, userID string, f ActivityFilter) ([]models.Activity, int, error) {
	const where = `WHERE a.user_id = $1
		   AND ($2::date IS NULL OR a.activity_date >= $2)
		   AND ($3::date IS NULL OR a.activity_date <= $3)
		   AND ($4::text IS NULL OR a.activity_type = $4)`
	args := []any{userID, f.From, f.To, nullIfEmpty(f.ActivityType)}

	var total int
	if err := s.db.QueryRow(ctx, `SELECT COUNT(*) FROM activities a `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	rows, err := s.db.Query(ctx, `SELECT `+activityColumns+` FROM activities a `+where+`
		 ORDER BY a.activity_date DESC, a.created_at DESC
		 LIMIT $5 OFFSET $6`, append(args, f.Limit, f.Offset)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	out := []models.Activity{}
	for rows.Next() {
		var a models.Activity
		if err := rows.Scan(activityDest(&a)...); err != nil {
			return nil, 0, err
		}
		out = append(out, a)
	}
	return out, total, rows.Err()
}

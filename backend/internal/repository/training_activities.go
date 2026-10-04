package repository

import (
	"context"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const plannedColumns = `ta.id, ta.training_block_id, ta.user_id, ta.activity_date, ta.activity_type, ta.title,
	ta.description, ta.planned_distance_km::float8, ta.planned_pace_seconds_per_km, ta.planned_duration_seconds,
	ta.status, ta.completion_reason, ta.completion_notes, ta.created_at, ta.updated_at`

func plannedDest(t *models.TrainingActivity, extra ...any) []any {
	return append([]any{&t.ID, &t.TrainingBlockID, &t.UserID, &t.ActivityDate, &t.ActivityType, &t.Title,
		&t.Description, &t.PlannedDistanceKm, &t.PlannedPaceSecondsPerKm, &t.PlannedDurationSeconds,
		&t.Status, &t.CompletionReason, &t.CompletionNotes, &t.CreatedAt, &t.UpdatedAt}, extra...)
}

type PlannedInput struct {
	ActivityDate            models.Date
	ActivityType            string
	Title                   string
	Description             *string
	PlannedDistanceKm       *float64
	PlannedPaceSecondsPerKm *int
	PlannedDurationSeconds  *int
}

func (s *Store) CreatePlanned(ctx context.Context, userID, blockID string, in PlannedInput) (*models.TrainingActivity, error) {
	var t models.TrainingActivity
	err := s.db.QueryRow(ctx, `
		INSERT INTO training_activities AS ta
		       (training_block_id, user_id, activity_date, activity_type, title, description,
		        planned_distance_km, planned_pace_seconds_per_km, planned_duration_seconds)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING `+plannedColumns,
		blockID, userID, in.ActivityDate, in.ActivityType, in.Title, in.Description,
		in.PlannedDistanceKm, in.PlannedPaceSecondsPerKm, in.PlannedDurationSeconds,
	).Scan(plannedDest(&t)...)
	if err != nil {
		return nil, err
	}
	return &t, nil
}

// GetPlanned loads a planned activity owned by userID. With forUpdate the row
// is locked for the surrounding transaction.
func (s *Store) GetPlanned(ctx context.Context, userID, id string, forUpdate bool) (*models.TrainingActivity, error) {
	q := `SELECT ` + plannedColumns + `, b.name
	        FROM training_activities ta JOIN training_blocks b ON b.id = ta.training_block_id
	       WHERE ta.id = $1 AND ta.user_id = $2`
	if forUpdate {
		q += ` FOR UPDATE OF ta`
	}
	var t models.TrainingActivity
	if err := s.db.QueryRow(ctx, q, id, userID).Scan(plannedDest(&t, &t.TrainingBlockName)...); err != nil {
		return nil, notFound(err)
	}
	return &t, nil
}

func (s *Store) UpdatePlanned(ctx context.Context, userID, id string, in PlannedInput) (*models.TrainingActivity, error) {
	var t models.TrainingActivity
	err := s.db.QueryRow(ctx, `
		UPDATE training_activities AS ta
		   SET activity_date = $3, activity_type = $4, title = $5, description = $6,
		       planned_distance_km = $7, planned_pace_seconds_per_km = $8, planned_duration_seconds = $9,
		       updated_at = now()
		 WHERE ta.id = $1 AND ta.user_id = $2
		RETURNING `+plannedColumns,
		id, userID, in.ActivityDate, in.ActivityType, in.Title, in.Description,
		in.PlannedDistanceKm, in.PlannedPaceSecondsPerKm, in.PlannedDurationSeconds,
	).Scan(plannedDest(&t)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &t, nil
}

func (s *Store) SetPlannedStatus(ctx context.Context, userID, id, status string, reason, notes *string) error {
	tag, err := s.db.Exec(ctx, `
		UPDATE training_activities
		   SET status = $3, completion_reason = $4, completion_notes = $5, updated_at = now()
		 WHERE id = $1 AND user_id = $2`, id, userID, status, reason, notes)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) DeletePlanned(ctx context.Context, userID, id string) error {
	tag, err := s.db.Exec(ctx, `DELETE FROM training_activities WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

type PlannedFilter struct {
	BlockID string // optional
	From    *models.Date
	To      *models.Date // inclusive
}

// ListPlanned returns planned activities (with their linked actual result,
// if any) ordered by date.
func (s *Store) ListPlanned(ctx context.Context, userID string, f PlannedFilter) ([]models.TrainingActivity, error) {
	rows, err := s.db.Query(ctx, `
		SELECT `+plannedColumns+`, b.name, `+activityColumns+`
		  FROM training_activities ta
		  JOIN training_blocks b ON b.id = ta.training_block_id
		  LEFT JOIN activities a ON a.training_activity_id = ta.id AND a.user_id = ta.user_id
		 WHERE ta.user_id = $1
		   AND ($2::uuid IS NULL OR ta.training_block_id = $2::uuid)
		   AND ($3::date IS NULL OR ta.activity_date >= $3)
		   AND ($4::date IS NULL OR ta.activity_date <= $4)
		 ORDER BY ta.activity_date, ta.created_at`,
		userID, nullIfEmpty(f.BlockID), f.From, f.To)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.TrainingActivity{}
	for rows.Next() {
		var t models.TrainingActivity
		var na nullableActivity
		if err := rows.Scan(plannedDest(&t, append([]any{&t.TrainingBlockName}, na.dest()...)...)...); err != nil {
			return nil, err
		}
		t.ActualActivity = na.toActivity()
		out = append(out, t)
	}
	return out, rows.Err()
}

func nullIfEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

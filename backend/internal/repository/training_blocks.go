package repository

import (
	"context"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const blockColumns = `b.id, b.user_id, b.name, b.description, b.start_date, b.end_date, b.goal, b.created_at, b.updated_at`

func scanBlockInto(b *models.TrainingBlock, dest ...any) []any {
	return append([]any{&b.ID, &b.UserID, &b.Name, &b.Description, &b.StartDate, &b.EndDate, &b.Goal, &b.CreatedAt, &b.UpdatedAt}, dest...)
}

type BlockInput struct {
	Name        string
	Description *string
	StartDate   models.Date
	EndDate     models.Date
	Goal        *string
}

func (s *Store) CreateBlock(ctx context.Context, userID string, in BlockInput) (*models.TrainingBlock, error) {
	var b models.TrainingBlock
	err := s.db.QueryRow(ctx, `
		INSERT INTO training_blocks AS b (user_id, name, description, start_date, end_date, goal)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING `+blockColumns,
		userID, in.Name, in.Description, in.StartDate, in.EndDate, in.Goal,
	).Scan(scanBlockInto(&b)...)
	if err != nil {
		return nil, err
	}
	return &b, nil
}

func (s *Store) GetBlock(ctx context.Context, userID, id string) (*models.TrainingBlock, error) {
	var b models.TrainingBlock
	err := s.db.QueryRow(ctx,
		`SELECT `+blockColumns+` FROM training_blocks b WHERE b.id = $1 AND b.user_id = $2`, id, userID,
	).Scan(scanBlockInto(&b)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &b, nil
}

func (s *Store) UpdateBlock(ctx context.Context, userID, id string, in BlockInput) (*models.TrainingBlock, error) {
	var b models.TrainingBlock
	err := s.db.QueryRow(ctx, `
		UPDATE training_blocks AS b
		   SET name = $3, description = $4, start_date = $5, end_date = $6, goal = $7, updated_at = now()
		 WHERE b.id = $1 AND b.user_id = $2
		RETURNING `+blockColumns,
		id, userID, in.Name, in.Description, in.StartDate, in.EndDate, in.Goal,
	).Scan(scanBlockInto(&b)...)
	if err != nil {
		return nil, notFound(err)
	}
	return &b, nil
}

func (s *Store) DeleteBlock(ctx context.Context, userID, id string) error {
	tag, err := s.db.Exec(ctx, `DELETE FROM training_blocks WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

// CountPlannedOutside counts a block's planned activities that would fall
// outside [start, end] — used to stop date edits from orphaning the plan.
func (s *Store) CountPlannedOutside(ctx context.Context, userID, blockID string, start, end models.Date) (int, error) {
	var n int
	err := s.db.QueryRow(ctx, `
		SELECT COUNT(*) FROM training_activities
		 WHERE training_block_id = $1 AND user_id = $2
		   AND (activity_date < $3 OR activity_date > $4)`,
		blockID, userID, start, end).Scan(&n)
	return n, err
}

// planSummarySelect aggregates training_activities aliased "ta". $today is
// the parameter holding the user's local date.
//
// Completion (v1): (completed + modified) / due non-rest activities, where an
// activity is "due" once its date has arrived or it has been resolved
// (completed, modified, skipped). Rest days never count. Future, still
// planned sessions are not counted against the runner.
func planSummarySelect(today string) string {
	return `
		COUNT(*),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest'),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest' AND ta.status = 'planned'),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest' AND ta.status = 'completed'),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest' AND ta.status = 'modified'),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest' AND ta.status = 'skipped'),
		COUNT(*) FILTER (WHERE ta.activity_type <> 'rest' AND (ta.status <> 'planned' OR ta.activity_date <= ` + today + `)),
		COALESCE(SUM(ta.planned_distance_km) FILTER (WHERE ta.status <> 'skipped'), 0)::float8`
}

func summaryDest(p *models.PlanSummary) []any {
	return []any{&p.TotalActivities, &p.SessionCount, &p.Planned, &p.Completed, &p.Modified, &p.Skipped, &p.Due, &p.PlannedDistanceKm}
}

// FinalizeSummary derives the completion percentage from the counts.
func FinalizeSummary(p *models.PlanSummary) {
	if p.Due > 0 {
		pct := float64(p.Completed+p.Modified) / float64(p.Due) * 100
		p.CompletionPct = &pct
	} else {
		p.CompletionPct = nil
	}
}

func (s *Store) ListBlocks(ctx context.Context, userID string, today models.Date, limit, offset int) ([]models.TrainingBlockWithSummary, int, error) {
	var total int
	if err := s.db.QueryRow(ctx, `SELECT COUNT(*) FROM training_blocks WHERE user_id = $1`, userID).Scan(&total); err != nil {
		return nil, 0, err
	}

	rows, err := s.db.Query(ctx, `
		SELECT `+blockColumns+`, t.*,
		       COALESCE((SELECT SUM(a.distance_km) FROM activities a
		                  WHERE a.user_id = b.user_id
		                    AND a.activity_date BETWEEN b.start_date AND b.end_date), 0)::float8
		  FROM training_blocks b
		  LEFT JOIN LATERAL (
		        SELECT `+planSummarySelect("$2")+`
		          FROM training_activities ta
		         WHERE ta.training_block_id = b.id AND ta.user_id = b.user_id
		  ) t ON true
		 WHERE b.user_id = $1
		 ORDER BY b.start_date DESC, b.created_at DESC
		 LIMIT $3 OFFSET $4`, userID, today, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	out := []models.TrainingBlockWithSummary{}
	for rows.Next() {
		var item models.TrainingBlockWithSummary
		dest := scanBlockInto(&item.TrainingBlock, summaryDest(&item.Summary)...)
		dest = append(dest, &item.Summary.ActualDistanceKm)
		if err := rows.Scan(dest...); err != nil {
			return nil, 0, err
		}
		FinalizeSummary(&item.Summary)
		out = append(out, item)
	}
	return out, total, rows.Err()
}

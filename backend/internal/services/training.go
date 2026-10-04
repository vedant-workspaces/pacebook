package services

import (
	"context"
	"errors"
	"regexp"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

var uuidRe = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

func IsUUID(s string) bool { return uuidRe.MatchString(s) }

type TrainingService struct{ store *repository.Store }

func NewTrainingService(store *repository.Store) *TrainingService { return &TrainingService{store} }

// ---- Training blocks ----

type BlockRequest struct {
	Name        string      `json:"name"`
	Description *string     `json:"description"`
	StartDate   models.Date `json:"startDate"`
	EndDate     models.Date `json:"endDate"`
	Goal        *string     `json:"goal"`
}

func (r BlockRequest) toInput() (repository.BlockInput, error) {
	in := repository.BlockInput{
		StartDate:   r.StartDate,
		EndDate:     r.EndDate,
		Description: cleanText(r.Description),
		Goal:        cleanText(r.Goal),
	}
	var err error
	if in.Name, err = requireText("name", r.Name, MaxNameLength); err != nil {
		return in, err
	}
	if err := validateDate("startDate", in.StartDate); err != nil {
		return in, err
	}
	if err := validateDate("endDate", in.EndDate); err != nil {
		return in, err
	}
	if in.EndDate.Before(in.StartDate) {
		return in, invalid("endDate", "End date must be on or after the start date")
	}
	if in.StartDate.DaysUntil(in.EndDate) > MaxBlockLengthDays {
		return in, invalid("endDate", "A training block can be at most two years long")
	}
	if err := checkLength("description", in.Description, MaxDescriptionLength); err != nil {
		return in, err
	}
	if err := checkLength("goal", in.Goal, MaxGoalLength); err != nil {
		return in, err
	}
	return in, nil
}

func (s *TrainingService) ListBlocks(ctx context.Context, userID string, today models.Date, page, limit int) ([]models.TrainingBlockWithSummary, models.Pagination, error) {
	items, total, err := s.store.ListBlocks(ctx, userID, today, limit, (page-1)*limit)
	return items, models.Pagination{Page: page, Limit: limit, Total: total}, err
}

func (s *TrainingService) CreateBlock(ctx context.Context, userID string, req BlockRequest) (*models.TrainingBlock, error) {
	in, err := req.toInput()
	if err != nil {
		return nil, err
	}
	return s.store.CreateBlock(ctx, userID, in)
}

func (s *TrainingService) GetBlock(ctx context.Context, userID, id string) (*models.TrainingBlock, error) {
	return s.store.GetBlock(ctx, userID, id)
}

func (s *TrainingService) UpdateBlock(ctx context.Context, userID, id string, req BlockRequest) (*models.TrainingBlock, error) {
	in, err := req.toInput()
	if err != nil {
		return nil, err
	}
	var out *models.TrainingBlock
	err = s.store.InTx(ctx, func(tx *repository.Store) error {
		if _, err := tx.GetBlock(ctx, userID, id); err != nil {
			return err
		}
		n, err := tx.CountPlannedOutside(ctx, userID, id, in.StartDate, in.EndDate)
		if err != nil {
			return err
		}
		if n > 0 {
			return invalid("startDate", "%d planned activit%s fall outside the new dates. Move or delete them first.", n, plural(n, "y", "ies"))
		}
		out, err = tx.UpdateBlock(ctx, userID, id, in)
		return err
	})
	return out, err
}

func plural(n int, one, many string) string {
	if n == 1 {
		return one
	}
	return many
}

// DeleteBlock deletes a block and its planned activities. Actual activities
// that were linked to them are kept as unplanned activities.
func (s *TrainingService) DeleteBlock(ctx context.Context, userID, id string) error {
	return s.store.DeleteBlock(ctx, userID, id)
}

type BlockStats struct {
	Summary                models.PlanSummary     `json:"summary"`
	LongestActivity        *models.Activity       `json:"longestActivity"`
	AveragePlannedDistance *float64               `json:"averagePlannedDistanceKm"`
	AverageActualDistance  *float64               `json:"averageActualDistanceKm"`
	AveragePace            *float64               `json:"averagePaceSecondsPerKm"`
	Weeks                  []repository.BlockWeek `json:"weeks"`
}

// BlockStats aggregates a block. Planned figures come from its planned
// activities; actual figures from all actual activities dated within the
// block (including unplanned ones), because that is the training done.
func (s *TrainingService) BlockStats(ctx context.Context, userID, id string, today models.Date) (*BlockStats, error) {
	b, err := s.store.GetBlock(ctx, userID, id)
	if err != nil {
		return nil, err
	}
	from, to := b.StartDate, b.EndDate.AddDays(1)
	out := &BlockStats{}
	if out.Summary, err = s.store.PlanSummary(ctx, userID, b.ID, &from, &to, today); err != nil {
		return nil, err
	}
	if out.LongestActivity, err = s.store.LongestActivity(ctx, userID, &from, &to); err != nil {
		return nil, err
	}
	if out.AveragePlannedDistance, err = s.store.AveragePlannedDistance(ctx, userID, b.ID); err != nil {
		return nil, err
	}
	agg, err := s.store.Aggregates(ctx, userID, &from, &to)
	if err != nil {
		return nil, err
	}
	out.AverageActualDistance = agg.AvgDistanceKm
	out.AveragePace = agg.AvgPaceSecPerKm
	if out.Weeks, err = s.store.BlockWeeks(ctx, userID, b, today); err != nil {
		return nil, err
	}
	return out, nil
}

// ---- Planned activities ----

type PlannedRequest struct {
	ActivityDate            models.Date `json:"activityDate"`
	ActivityType            string      `json:"activityType"`
	Title                   string      `json:"title"`
	Description             *string     `json:"description"`
	PlannedDistanceKm       *float64    `json:"plannedDistanceKm"`
	PlannedPaceSecondsPerKm *int        `json:"plannedPaceSecondsPerKm"`
	PlannedDurationSeconds  *int        `json:"plannedDurationSeconds"`
}

func (r PlannedRequest) toInput(b *models.TrainingBlock) (repository.PlannedInput, error) {
	in := repository.PlannedInput{
		ActivityDate:            r.ActivityDate,
		ActivityType:            r.ActivityType,
		Description:             cleanText(r.Description),
		PlannedDistanceKm:       roundDistance(r.PlannedDistanceKm),
		PlannedPaceSecondsPerKm: r.PlannedPaceSecondsPerKm,
		PlannedDurationSeconds:  r.PlannedDurationSeconds,
	}
	if err := validateDate("activityDate", in.ActivityDate); err != nil {
		return in, err
	}
	if in.ActivityDate.Before(b.StartDate) || in.ActivityDate.After(b.EndDate) {
		return in, invalid("activityDate", "Date must be within the training block (%s to %s)", b.StartDate, b.EndDate)
	}
	if err := validateActivityType("activityType", in.ActivityType); err != nil {
		return in, err
	}
	title := cleanText(&r.Title)
	if title == nil {
		in.Title = models.ActivityTypeLabel(in.ActivityType)
	} else {
		in.Title = *title
	}
	if err := checkLength("title", &in.Title, MaxNameLength); err != nil {
		return in, err
	}
	if err := checkLength("description", in.Description, MaxDescriptionLength); err != nil {
		return in, err
	}
	if err := ValidatePlannedDistance(in.PlannedDistanceKm); err != nil {
		return in, err
	}
	if err := ValidatePace("plannedPaceSecondsPerKm", in.PlannedPaceSecondsPerKm); err != nil {
		return in, err
	}
	if err := validateDuration(in.PlannedDurationSeconds); err != nil {
		return in, err
	}
	return in, nil
}

func (s *TrainingService) ListPlanned(ctx context.Context, userID, blockID string, from, to *models.Date) ([]models.TrainingActivity, error) {
	if _, err := s.store.GetBlock(ctx, userID, blockID); err != nil {
		return nil, err
	}
	return s.store.ListPlanned(ctx, userID, repository.PlannedFilter{BlockID: blockID, From: from, To: to})
}

func (s *TrainingService) CreatePlanned(ctx context.Context, userID, blockID string, req PlannedRequest) (*models.TrainingActivity, error) {
	b, err := s.store.GetBlock(ctx, userID, blockID)
	if err != nil {
		return nil, err
	}
	in, err := req.toInput(b)
	if err != nil {
		return nil, err
	}
	return s.store.CreatePlanned(ctx, userID, b.ID, in)
}

// GetPlanned returns a planned activity with its linked actual activity.
func (s *TrainingService) GetPlanned(ctx context.Context, userID, id string) (*models.TrainingActivity, error) {
	t, err := s.store.GetPlanned(ctx, userID, id, false)
	if err != nil {
		return nil, err
	}
	a, err := s.store.GetActivityByPlanned(ctx, userID, id)
	if err != nil && !errors.Is(err, repository.ErrNotFound) {
		return nil, err
	}
	t.ActualActivity = a
	return t, nil
}

func (s *TrainingService) UpdatePlanned(ctx context.Context, userID, id string, req PlannedRequest) (*models.TrainingActivity, error) {
	t, err := s.store.GetPlanned(ctx, userID, id, false)
	if err != nil {
		return nil, err
	}
	b, err := s.store.GetBlock(ctx, userID, t.TrainingBlockID)
	if err != nil {
		return nil, err
	}
	in, err := req.toInput(b)
	if err != nil {
		return nil, err
	}
	if _, err := s.store.UpdatePlanned(ctx, userID, id, in); err != nil {
		return nil, err
	}
	return s.GetPlanned(ctx, userID, id)
}

// DeletePlanned deletes a planned activity. A linked actual activity is kept
// and becomes unplanned (the FK sets its training_activity_id to NULL).
func (s *TrainingService) DeletePlanned(ctx context.Context, userID, id string) error {
	return s.store.DeletePlanned(ctx, userID, id)
}

// CompleteRequest records the actual result of a planned activity. For
// "complete", type and title default to the planned ones; for "modify" the
// runner may say what they actually did instead.
type CompleteRequest struct {
	ActivityDate     *models.Date `json:"activityDate"`
	ActivityType     string       `json:"activityType"`
	Title            string       `json:"title"`
	DistanceKm       *float64     `json:"distanceKm"`
	PaceSecondsPerKm *int         `json:"paceSecondsPerKm"`
	AverageHeartRate *int         `json:"averageHeartRate"`
	Comment          *string      `json:"comment"`
}

func (s *TrainingService) Complete(ctx context.Context, userID, id string, req CompleteRequest) (*models.TrainingActivity, error) {
	return s.recordResult(ctx, userID, id, req, models.StatusCompleted)
}

func (s *TrainingService) Modify(ctx context.Context, userID, id string, req CompleteRequest) (*models.TrainingActivity, error) {
	return s.recordResult(ctx, userID, id, req, models.StatusModified)
}

// recordResult creates (or, if one exists, updates) the actual activity
// linked to the planned activity and sets the planned status, atomically.
func (s *TrainingService) recordResult(ctx context.Context, userID, id string, req CompleteRequest, status string) (*models.TrainingActivity, error) {
	err := s.store.InTx(ctx, func(tx *repository.Store) error {
		planned, err := tx.GetPlanned(ctx, userID, id, true)
		if err != nil {
			return err
		}
		ar := ActivityRequest{
			ActivityDate:     planned.ActivityDate,
			ActivityType:     planned.ActivityType,
			Title:            planned.Title,
			DistanceKm:       req.DistanceKm,
			PaceSecondsPerKm: req.PaceSecondsPerKm,
			AverageHeartRate: req.AverageHeartRate,
			Comment:          req.Comment,
		}
		if req.ActivityDate != nil && !req.ActivityDate.IsZero() {
			ar.ActivityDate = *req.ActivityDate
		}
		if req.ActivityType != "" {
			ar.ActivityType = req.ActivityType
		}
		if t := cleanText(&req.Title); t != nil {
			ar.Title = *t
		} else if req.ActivityType != "" && req.ActivityType != planned.ActivityType {
			ar.Title = models.ActivityTypeLabel(req.ActivityType)
		}
		in, err := ar.toInput()
		if err != nil {
			return err
		}

		existing, err := tx.GetActivityByPlanned(ctx, userID, planned.ID)
		switch {
		case errors.Is(err, repository.ErrNotFound):
			if _, err := tx.CreateActivity(ctx, userID, &planned.ID, in); err != nil {
				return err
			}
		case err != nil:
			return err
		default:
			if _, err := tx.UpdateActivity(ctx, userID, existing.ID, in); err != nil {
				return err
			}
		}
		return tx.SetPlannedStatus(ctx, userID, planned.ID, status, nil, nil)
	})
	if err != nil {
		return nil, err
	}
	return s.GetPlanned(ctx, userID, id)
}

type SkipRequest struct {
	Reason string  `json:"reason"`
	Notes  *string `json:"notes"`
}

// Skip marks a planned activity as not completed with a reason. No actual
// activity is created. If a result was already logged it must be deleted
// first, so mileage never silently disappears.
func (s *TrainingService) Skip(ctx context.Context, userID, id string, req SkipRequest) (*models.TrainingActivity, error) {
	if !models.IsValidCompletionReason(req.Reason) {
		return nil, invalid("reason", "Please choose a reason")
	}
	notes := cleanText(req.Notes)
	if err := checkLength("notes", notes, MaxCommentLength); err != nil {
		return nil, err
	}
	err := s.store.InTx(ctx, func(tx *repository.Store) error {
		planned, err := tx.GetPlanned(ctx, userID, id, true)
		if err != nil {
			return err
		}
		if err := ensureNoResult(ctx, tx, userID, planned.ID); err != nil {
			return err
		}
		return tx.SetPlannedStatus(ctx, userID, planned.ID, models.StatusSkipped, &req.Reason, notes)
	})
	if err != nil {
		return nil, err
	}
	return s.GetPlanned(ctx, userID, id)
}

// Reset returns a skipped activity to "planned".
func (s *TrainingService) Reset(ctx context.Context, userID, id string) (*models.TrainingActivity, error) {
	err := s.store.InTx(ctx, func(tx *repository.Store) error {
		planned, err := tx.GetPlanned(ctx, userID, id, true)
		if err != nil {
			return err
		}
		if err := ensureNoResult(ctx, tx, userID, planned.ID); err != nil {
			return err
		}
		return tx.SetPlannedStatus(ctx, userID, planned.ID, models.StatusPlanned, nil, nil)
	})
	if err != nil {
		return nil, err
	}
	return s.GetPlanned(ctx, userID, id)
}

func ensureNoResult(ctx context.Context, tx *repository.Store, userID, plannedID string) error {
	_, err := tx.GetActivityByPlanned(ctx, userID, plannedID)
	if err == nil {
		return &ConflictError{"This activity has a logged result. Delete the logged activity first."}
	}
	if errors.Is(err, repository.ErrNotFound) {
		return nil
	}
	return err
}

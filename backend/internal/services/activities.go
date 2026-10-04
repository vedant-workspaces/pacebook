package services

import (
	"context"
	"errors"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

type ActivityService struct{ store *repository.Store }

func NewActivityService(store *repository.Store) *ActivityService { return &ActivityService{store} }

// ActivityRequest is the body for creating/updating an actual activity.
type ActivityRequest struct {
	TrainingActivityID *string     `json:"trainingActivityId"`
	ActivityDate       models.Date `json:"activityDate"`
	ActivityType       string      `json:"activityType"`
	Title              string      `json:"title"`
	DistanceKm         *float64    `json:"distanceKm"`
	PaceSecondsPerKm   *int        `json:"paceSecondsPerKm"`
	AverageHeartRate   *int        `json:"averageHeartRate"`
	Comment            *string     `json:"comment"`
}

func (r ActivityRequest) toInput() (repository.ActivityInput, error) {
	in := repository.ActivityInput{
		ActivityDate:     r.ActivityDate,
		ActivityType:     r.ActivityType,
		DistanceKm:       roundDistance(r.DistanceKm),
		PaceSecondsPerKm: r.PaceSecondsPerKm,
		AverageHeartRate: r.AverageHeartRate,
		Comment:          cleanText(r.Comment),
	}
	if err := validateDate("activityDate", in.ActivityDate); err != nil {
		return in, err
	}
	if err := validateActivityType("activityType", in.ActivityType); err != nil {
		return in, err
	}
	if err := ValidateActualDistance(in.DistanceKm); err != nil {
		return in, err
	}
	if err := ValidatePace("paceSecondsPerKm", in.PaceSecondsPerKm); err != nil {
		return in, err
	}
	if err := ValidateHeartRate(in.AverageHeartRate); err != nil {
		return in, err
	}
	if err := checkLength("comment", in.Comment, MaxCommentLength); err != nil {
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
	return in, nil
}

type ListActivitiesParams struct {
	From, To     *models.Date
	ActivityType string
	Page, Limit  int
}

func (s *ActivityService) List(ctx context.Context, userID string, p ListActivitiesParams) ([]models.Activity, models.Pagination, error) {
	items, total, err := s.store.ListActivities(ctx, userID, repository.ActivityFilter{
		From: p.From, To: p.To, ActivityType: p.ActivityType,
		Limit: p.Limit, Offset: (p.Page - 1) * p.Limit,
	})
	return items, models.Pagination{Page: p.Page, Limit: p.Limit, Total: total}, err
}

// Get returns an activity with its linked planned activity (if any).
func (s *ActivityService) Get(ctx context.Context, userID, id string) (*models.Activity, error) {
	a, err := s.store.GetActivity(ctx, userID, id)
	if err != nil {
		return nil, err
	}
	if a.TrainingActivityID != nil {
		planned, err := s.store.GetPlanned(ctx, userID, *a.TrainingActivityID, false)
		if err != nil && !errors.Is(err, repository.ErrNotFound) {
			return nil, err
		}
		a.PlannedActivity = planned
	}
	return a, nil
}

// Create logs an actual activity. Without trainingActivityId it is an
// unplanned activity. With one, the planned activity must belong to the
// caller and not already have a result; it is then marked completed.
func (s *ActivityService) Create(ctx context.Context, userID string, req ActivityRequest) (*models.Activity, error) {
	in, err := req.toInput()
	if err != nil {
		return nil, err
	}
	linkID := cleanText(req.TrainingActivityID)
	if linkID == nil {
		return s.store.CreateActivity(ctx, userID, nil, in)
	}
	if !IsUUID(*linkID) {
		return nil, invalid("trainingActivityId", "Planned activity not found")
	}

	var created *models.Activity
	err = s.store.InTx(ctx, func(tx *repository.Store) error {
		planned, err := tx.GetPlanned(ctx, userID, *linkID, true)
		if errors.Is(err, repository.ErrNotFound) {
			// Never reveal whether the ID exists for another user.
			return invalid("trainingActivityId", "Planned activity not found")
		}
		if err != nil {
			return err
		}
		created, err = tx.CreateActivity(ctx, userID, &planned.ID, in)
		if errors.Is(err, repository.ErrConflict) {
			return &ConflictError{"This planned activity already has a logged result"}
		}
		if err != nil {
			return err
		}
		return tx.SetPlannedStatus(ctx, userID, planned.ID, models.StatusCompleted, nil, nil)
	})
	return created, err
}

// Update edits an activity in place. The link to a planned activity is
// preserved (trainingActivityId in the body is ignored).
func (s *ActivityService) Update(ctx context.Context, userID, id string, req ActivityRequest) (*models.Activity, error) {
	in, err := req.toInput()
	if err != nil {
		return nil, err
	}
	if _, err := s.store.UpdateActivity(ctx, userID, id, in); err != nil {
		return nil, err
	}
	return s.Get(ctx, userID, id)
}

// Delete removes an activity. A linked planned activity is kept and returns
// to "planned" so it can be completed or skipped again.
func (s *ActivityService) Delete(ctx context.Context, userID, id string) error {
	return s.store.InTx(ctx, func(tx *repository.Store) error {
		a, err := tx.GetActivity(ctx, userID, id)
		if err != nil {
			return err
		}
		if err := tx.DeleteActivity(ctx, userID, id); err != nil {
			return err
		}
		if a.TrainingActivityID != nil {
			err := tx.SetPlannedStatus(ctx, userID, *a.TrainingActivityID, models.StatusPlanned, nil, nil)
			if err != nil && !errors.Is(err, repository.ErrNotFound) {
				return err
			}
		}
		return nil
	})
}

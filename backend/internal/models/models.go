// Package models holds the domain types shared across layers.
package models

import "time"

type User struct {
	ID             string    `json:"id"`
	GoogleID       string    `json:"-"`
	Email          string    `json:"email"`
	Name           string    `json:"name"`
	ProfilePicture *string   `json:"profilePicture,omitempty"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
}

type TrainingBlock struct {
	ID          string    `json:"id"`
	UserID      string    `json:"-"`
	Name        string    `json:"name"`
	Description *string   `json:"description,omitempty"`
	StartDate   Date      `json:"startDate"`
	EndDate     Date      `json:"endDate"`
	Goal        *string   `json:"goal,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// PlanSummary aggregates the planned activities of a block (or any range).
// See CompletionPct for the exact completion definition.
type PlanSummary struct {
	TotalActivities   int      `json:"totalActivities"`
	SessionCount      int      `json:"sessionCount"` // non-rest activities
	Planned           int      `json:"planned"`
	Completed         int      `json:"completed"`
	Modified          int      `json:"modified"`
	Skipped           int      `json:"skipped"`
	Due               int      `json:"due"`
	CompletionPct     *float64 `json:"completionPct"`
	PlannedDistanceKm float64  `json:"plannedDistanceKm"`
	ActualDistanceKm  float64  `json:"actualDistanceKm"`
}

type TrainingBlockWithSummary struct {
	TrainingBlock
	Summary PlanSummary `json:"summary"`
}

type TrainingActivity struct {
	ID                      string    `json:"id"`
	TrainingBlockID         string    `json:"trainingBlockId"`
	UserID                  string    `json:"-"`
	ActivityDate            Date      `json:"activityDate"`
	ActivityType            string    `json:"activityType"`
	Title                   string    `json:"title"`
	Description             *string   `json:"description,omitempty"`
	PlannedDistanceKm       *float64  `json:"plannedDistanceKm,omitempty"`
	PlannedPaceSecondsPerKm *int      `json:"plannedPaceSecondsPerKm,omitempty"`
	PlannedDurationSeconds  *int      `json:"plannedDurationSeconds,omitempty"`
	Status                  string    `json:"status"`
	CompletionReason        *string   `json:"completionReason,omitempty"`
	CompletionNotes         *string   `json:"completionNotes,omitempty"`
	CreatedAt               time.Time `json:"createdAt"`
	UpdatedAt               time.Time `json:"updatedAt"`

	// Populated on reads that join related rows.
	TrainingBlockName *string   `json:"trainingBlockName,omitempty"`
	ActualActivity    *Activity `json:"actualActivity,omitempty"`
}

type Activity struct {
	ID                 string    `json:"id"`
	UserID             string    `json:"-"`
	TrainingActivityID *string   `json:"trainingActivityId,omitempty"`
	ActivityDate       Date      `json:"activityDate"`
	ActivityType       string    `json:"activityType"`
	Title              string    `json:"title"`
	DistanceKm         *float64  `json:"distanceKm,omitempty"`
	PaceSecondsPerKm   *int      `json:"paceSecondsPerKm,omitempty"`
	AverageHeartRate   *int      `json:"averageHeartRate,omitempty"`
	Comment            *string   `json:"comment,omitempty"`
	Source             string    `json:"source"`
	CreatedAt          time.Time `json:"createdAt"`
	UpdatedAt          time.Time `json:"updatedAt"`

	// Populated on detail reads of linked activities.
	PlannedActivity *TrainingActivity `json:"plannedActivity,omitempty"`
}

type Pagination struct {
	Page  int `json:"page"`
	Limit int `json:"limit"`
	Total int `json:"total"`
}

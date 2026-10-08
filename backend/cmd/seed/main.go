// Command seed fills a development database with a sample training block,
// planned sessions and logged runs for one user. It refuses to run when
// APP_ENV=production.
//
//	go run ./cmd/seed -email runner@pacelog.local
//
// The user is created as a dev-login user if they don't exist yet, so the
// data is visible after signing in with "Dev sign-in" using the same email.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/database"
	"github.com/vedant-workspaces/pacebook/backend/internal/models"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
	"github.com/vedant-workspaces/pacebook/backend/internal/services"
)

func main() {
	if err := run(); err != nil {
		slog.Error("seed failed", "err", err)
		os.Exit(1)
	}
}

func run() error {
	email := flag.String("email", "runner@pacelog.local", "email of the user to seed")
	flag.Parse()

	if os.Getenv("APP_ENV") == "production" {
		return errors.New("refusing to seed sample data when APP_ENV=production")
	}
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		return errors.New("DATABASE_URL is required")
	}

	ctx := context.Background()
	pool, err := database.Connect(ctx, url)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err := database.Migrate(ctx, pool); err != nil {
		return err
	}

	store := repository.New(pool)
	addr := strings.ToLower(*email)
	var userID string
	err = pool.QueryRow(ctx, `SELECT id FROM users WHERE email = $1`, addr).Scan(&userID)
	if err != nil {
		u, err := store.UpsertGoogleUser(ctx, "dev:"+addr, addr, "Dev Runner", nil)
		if err != nil {
			return err
		}
		userID = u.ID
	}

	training := services.NewTrainingService(store)
	activities := services.NewActivityService(store)

	today := models.DateOf(time.Now())
	start := today.WeekStart().AddDays(-21) // three completed weeks behind us
	end := start.AddDays(16*7 - 1)          // a 16-week block

	block, err := training.CreateBlock(ctx, userID, services.BlockRequest{
		Name:        "Autumn Marathon Build",
		Description: ptr("16 weeks of structured marathon training."),
		StartDate:   start,
		EndDate:     end,
		Goal:        ptr("Sub 4:00 Marathon"),
	})
	if err != nil {
		return err
	}

	type session struct {
		typ   string
		title string
		km    float64
		pace  int
	}
	week := []session{
		{"easy_run", "Easy Aerobic Run", 8, 345},
		{"intervals", "6 × 1 km @ 4:30", 10, 300},
		{"rest", "Rest", 0, 0},
		{"tempo", "Tempo @ 5:00", 10, 300},
		{"strength", "Strength & Mobility", 0, 0},
		{"long_run", "Long Run", 22, 360},
		{"recovery", "Recovery Run", 6, 380},
	}

	created := 0
	for w := 0; w < 16; w++ {
		bump := float64(w%4) * 0.1 // progressive overload, cut-back every 4th week
		if w%4 == 3 {
			bump = -0.2
		}
		for i, s := range week {
			date := start.AddDays(w*7 + i)
			req := services.PlannedRequest{ActivityDate: date, ActivityType: s.typ, Title: s.title}
			if s.km > 0 {
				req.PlannedDistanceKm = ptr(round1(s.km * (1 + bump)))
			}
			if s.pace > 0 {
				req.PlannedPaceSecondsPerKm = ptr(s.pace)
			}
			if s.typ == "strength" {
				req.PlannedDurationSeconds = ptr(45 * 60)
			}
			planned, err := training.CreatePlanned(ctx, userID, block.ID, req)
			if err != nil {
				return fmt.Errorf("planned %s: %w", date, err)
			}
			created++

			if !date.Before(today) || s.typ == "rest" {
				continue
			}
			n := w*7 + i
			switch {
			case n%11 == 3:
				_, err = training.Skip(ctx, userID, planned.ID, services.SkipRequest{Reason: "fatigue", Notes: ptr("Legs still heavy from the weekend.")})
			case n%13 == 5:
				_, err = training.Modify(ctx, userID, planned.ID, services.CompleteRequest{
					ActivityType: "easy_run", DistanceKm: ptr(7.0), PaceSecondsPerKm: ptr(355), AverageHeartRate: ptr(150),
					Comment: ptr("Didn't have it today — switched to easy running."),
				})
			case s.km > 0:
				_, err = training.Complete(ctx, userID, planned.ID, services.CompleteRequest{
					DistanceKm:       ptr(round1(*req.PlannedDistanceKm + float64(n%5-2)*0.15)),
					PaceSecondsPerKm: ptr(s.pace - 6 + n%12),
					AverageHeartRate: ptr(140 + (n*7)%30),
					Comment:          ptr(comments[n%len(comments)]),
				})
			default:
				_, err = training.Complete(ctx, userID, planned.ID, services.CompleteRequest{})
			}
			if err != nil {
				return fmt.Errorf("result %s: %w", date, err)
			}
		}
	}

	// A few spontaneous runs that weren't on the plan.
	for _, d := range []int{-2, -9, -17} {
		_, err := activities.Create(ctx, userID, services.ActivityRequest{
			ActivityDate: today.AddDays(d), ActivityType: "recovery", Title: "Evening shake-out",
			DistanceKm: ptr(4.2), PaceSecondsPerKm: ptr(380), AverageHeartRate: ptr(139),
			Comment: ptr("Unplanned, just felt like moving."),
		})
		if err != nil {
			return err
		}
	}

	slog.Info("seeded sample data", "email", addr, "block", block.Name, "planned_activities", created)
	return nil
}

var comments = []string{
	"Felt strong today.",
	"Steady effort, good rhythm.",
	"Windy on the way back.",
	"Last two reps were tough.",
	"Easy and relaxed.",
}

func ptr[T any](v T) *T { return &v }

func round1(v float64) float64 { return float64(int(v*10+0.5)) / 10 }

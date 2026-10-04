package models

// Statuses of a planned training activity.
const (
	StatusPlanned   = "planned"
	StatusCompleted = "completed"
	StatusSkipped   = "skipped"
	StatusModified  = "modified"
)

const ActivityTypeRest = "rest"

const SourceManual = "manual"

type Option struct {
	Value string `json:"value"`
	Label string `json:"label"`
	// IsRun marks types whose distance/pace are expected (used by the UI to
	// decide which fields to emphasise). All distances count toward mileage.
	IsRun bool `json:"isRun,omitempty"`
}

// ActivityTypes is the single source of truth for activity types. The
// frontend loads it from GET /api/meta, so adding a type here is enough.
var ActivityTypes = []Option{
	{"easy_run", "Easy Run", true},
	{"long_run", "Long Run", true},
	{"recovery", "Recovery Run", true},
	{"tempo", "Tempo Run", true},
	{"intervals", "Intervals", true},
	{"fartlek", "Fartlek", true},
	{"hill_repeats", "Hill Repeats", true},
	{"race", "Race", true},
	{"trail_run", "Trail Run", true},
	{"treadmill", "Treadmill", true},
	{"walk", "Walk", false},
	{"strength", "Strength", false},
	{"cross_training", "Cross Training", false},
	{"rest", "Rest", false},
	{"other", "Other", false},
}

var CompletionReasons = []Option{
	{Value: "injury", Label: "Injury"},
	{Value: "illness", Label: "Illness"},
	{Value: "fatigue", Label: "Fatigue"},
	{Value: "work", Label: "Work"},
	{Value: "travel", Label: "Travel"},
	{Value: "weather", Label: "Weather"},
	{Value: "lack_of_time", Label: "Lack of Time"},
	{Value: "recovery", Label: "Recovery"},
	{Value: "personal", Label: "Personal"},
	{Value: "other", Label: "Other"},
}

func findOption(opts []Option, v string) (Option, bool) {
	for _, o := range opts {
		if o.Value == v {
			return o, true
		}
	}
	return Option{}, false
}

func IsValidActivityType(v string) bool { _, ok := findOption(ActivityTypes, v); return ok }

func IsValidCompletionReason(v string) bool { _, ok := findOption(CompletionReasons, v); return ok }

func ActivityTypeLabel(v string) string {
	if o, ok := findOption(ActivityTypes, v); ok {
		return o.Label
	}
	return v
}

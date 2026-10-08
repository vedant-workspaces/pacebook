package handlers_test

// Integration tests: they run the full router against a real PostgreSQL
// database named by TEST_DATABASE_URL (skipped when unset). Every table is
// truncated before each test.

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/vedant-workspaces/pacebook/backend/config"
	"github.com/vedant-workspaces/pacebook/backend/internal/auth"
	"github.com/vedant-workspaces/pacebook/backend/internal/database"
	"github.com/vedant-workspaces/pacebook/backend/internal/handlers"
	"github.com/vedant-workspaces/pacebook/backend/internal/repository"
)

const testSecret = "test-secret-test-secret-test-secret"

type env struct {
	t     *testing.T
	srv   *httptest.Server
	pool  *pgxpool.Pool
	store *repository.Store
}

func setup(t *testing.T) *env {
	t.Helper()
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL not set; skipping integration test")
	}
	ctx := context.Background()
	pool, err := database.Connect(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	if err := database.Migrate(ctx, pool); err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, `TRUNCATE users, training_blocks, training_activities, activities, sessions CASCADE`); err != nil {
		t.Fatal(err)
	}
	cfg := &config.Config{
		Env:            "test",
		DatabaseURL:    url,
		SessionSecret:  testSecret,
		SessionTTL:     time.Hour,
		FrontendURL:    "http://localhost:5173",
		AllowedOrigins: []string{"http://localhost:5173"},
	}
	srv := httptest.NewServer(handlers.NewRouter(cfg, pool))
	t.Cleanup(srv.Close)
	return &env{t: t, srv: srv, pool: pool, store: repository.New(pool)}
}

type client struct {
	e    *env
	http *http.Client
	id   string
}

// user creates a user with a live session and returns a client carrying
// that session cookie.
func (e *env) user(name string) *client {
	e.t.Helper()
	ctx := context.Background()
	u, err := e.store.UpsertGoogleUser(ctx, "google-"+name, name+"@example.com", name, nil)
	if err != nil {
		e.t.Fatal(err)
	}
	cookie, err := auth.NewSessions(e.store, testSecret, time.Hour, false).Create(ctx, u.ID)
	if err != nil {
		e.t.Fatal(err)
	}
	return e.clientWithCookie(u.ID, cookie)
}

func (e *env) anonymous() *client { return e.clientWithCookie("", nil) }

func (e *env) clientWithCookie(id string, c *http.Cookie) *client {
	jar, _ := cookiejar.New(nil)
	if c != nil {
		req, _ := http.NewRequest("GET", e.srv.URL, nil)
		jar.SetCookies(req.URL, []*http.Cookie{c})
	}
	return &client{e: e, http: &http.Client{Jar: jar}, id: id}
}

type response struct {
	Status int
	Body   map[string]any
}

func (r response) data() map[string]any {
	d, _ := r.Body["data"].(map[string]any)
	return d
}

func (r response) list() []any {
	d, _ := r.Body["data"].([]any)
	return d
}

func (r response) errCode() string {
	e, _ := r.Body["error"].(map[string]any)
	s, _ := e["code"].(string)
	return s
}

func (c *client) do(method, path string, body any) response {
	c.e.t.Helper()
	var rdr io.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rdr = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, c.e.srv.URL+path, rdr)
	if err != nil {
		c.e.t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Requested-With", "pacelog")
	res, err := c.http.Do(req)
	if err != nil {
		c.e.t.Fatal(err)
	}
	defer res.Body.Close()
	out := response{Status: res.StatusCode, Body: map[string]any{}}
	raw, _ := io.ReadAll(res.Body)
	if len(raw) > 0 {
		if err := json.Unmarshal(raw, &out.Body); err != nil {
			c.e.t.Fatalf("%s %s: invalid JSON %q", method, path, raw)
		}
	}
	return out
}

func (c *client) must(status int, method, path string, body any) response {
	c.e.t.Helper()
	r := c.do(method, path, body)
	if r.Status != status {
		c.e.t.Fatalf("%s %s: status %d, want %d; body %v", method, path, r.Status, status, r.Body)
	}
	return r
}

func (c *client) createBlock(start, end string) string {
	c.e.t.Helper()
	r := c.must(201, "POST", "/api/training-blocks", map[string]any{
		"name": "Marathon Block", "startDate": start, "endDate": end, "goal": "Sub 4:00",
	})
	return r.data()["id"].(string)
}

func (c *client) createPlanned(blockID, date, typ string, km float64) string {
	c.e.t.Helper()
	body := map[string]any{"activityDate": date, "activityType": typ, "title": typ}
	if km > 0 {
		body["plannedDistanceKm"] = km
	}
	r := c.must(201, "POST", "/api/training-blocks/"+blockID+"/activities", body)
	return r.data()["id"].(string)
}

func (c *client) logRun(date string, km float64) string {
	c.e.t.Helper()
	r := c.must(201, "POST", "/api/activities", map[string]any{
		"activityDate": date, "activityType": "easy_run", "distanceKm": km, "paceSecondsPerKm": 330,
	})
	return r.data()["id"].(string)
}

func num(v any) float64 {
	f, _ := v.(float64)
	return f
}

func approx(t *testing.T, label string, got, want float64) {
	t.Helper()
	if math.Abs(got-want) > 1e-6 {
		t.Errorf("%s = %v, want %v", label, got, want)
	}
}

// ---------------------------------------------------------------- auth ----

func TestUnauthenticatedRequestsAreRejected(t *testing.T) {
	e := setup(t)
	anon := e.anonymous()
	for _, p := range []string{
		"/api/auth/me", "/api/activities", "/api/training-blocks", "/api/stats/overview",
		"/api/stats/day", "/api/meta",
	} {
		if r := anon.do("GET", p, nil); r.Status != 401 || r.errCode() != "UNAUTHORIZED" {
			t.Errorf("GET %s: got %d %v, want 401 UNAUTHORIZED", p, r.Status, r.Body)
		}
	}
	if r := anon.do("POST", "/api/activities", map[string]any{"activityDate": "2026-10-04", "activityType": "easy_run"}); r.Status != 401 {
		t.Errorf("POST /api/activities: got %d, want 401", r.Status)
	}
}

func TestAuthenticatedUserCanAccess(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	r := alice.must(200, "GET", "/api/auth/me", nil)
	if r.data()["email"] != "alice@example.com" {
		t.Fatalf("unexpected me: %v", r.Body)
	}
	alice.must(200, "GET", "/api/activities", nil)
}

func TestLogoutEndsSession(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	alice.must(200, "POST", "/api/auth/logout", nil)
	alice.must(401, "GET", "/api/auth/me", nil)
}

func TestCSRFHeaderRequiredForWrites(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	req, _ := http.NewRequest("POST", e.srv.URL+"/api/activities", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("Content-Type", "application/json")
	res, err := alice.http.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	res.Body.Close()
	if res.StatusCode != 403 {
		t.Fatalf("missing CSRF header: got %d, want 403", res.StatusCode)
	}

	req, _ = http.NewRequest("POST", e.srv.URL+"/api/activities", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("X-Requested-With", "pacelog")
	req.Header.Set("Origin", "https://evil.example")
	res, err = alice.http.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	res.Body.Close()
	if res.StatusCode != 403 {
		t.Fatalf("foreign origin: got %d, want 403", res.StatusCode)
	}
}

func TestGoogleRoutesWithoutConfigReturn404(t *testing.T) {
	e := setup(t)
	e.anonymous().must(404, "GET", "/api/auth/google", nil)
	r := e.anonymous().must(200, "GET", "/api/auth/config", nil)
	if r.data()["googleEnabled"] != false || r.data()["devLoginEnabled"] != false {
		t.Fatalf("unexpected auth config %v", r.Body)
	}
	// Dev login is never routed unless explicitly enabled in development.
	e.anonymous().must(404, "POST", "/api/auth/dev-login", map[string]any{})
}

// ---------------------------------------------------- actual activities ----

func TestCreateValidActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	r := alice.must(201, "POST", "/api/activities", map[string]any{
		"activityDate": "2026-10-04", "activityType": "easy_run", "distanceKm": 8.4,
		"paceSecondsPerKm": 338, "averageHeartRate": 151, "comment": "Felt strong today.",
	})
	d := r.data()
	if d["title"] != "Easy Run" || num(d["distanceKm"]) != 8.4 || d["activityDate"] != "2026-10-04" {
		t.Fatalf("unexpected activity %v", d)
	}
	if _, linked := d["trainingActivityId"]; linked {
		t.Fatalf("unplanned activity must not be linked: %v", d)
	}
}

func TestActivityValidation(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	base := func() map[string]any {
		return map[string]any{"activityDate": "2026-10-04", "activityType": "easy_run", "distanceKm": 5.0}
	}
	cases := []struct {
		name  string
		field string
		value any
	}{
		{"zero distance", "distanceKm", 0},
		{"negative distance", "distanceKm", -3},
		{"huge distance", "distanceKm", 500},
		{"pace too fast", "paceSecondsPerKm", 60},
		{"pace too slow", "paceSecondsPerKm", 4000},
		{"hr too low", "averageHeartRate", 20},
		{"hr too high", "averageHeartRate", 260},
		{"bad date", "activityDate", "2026-02-30"},
		{"missing date", "activityDate", ""},
		{"unknown type", "activityType", "skydiving"},
		{"long comment", "comment", string(bytes.Repeat([]byte("x"), 2001))},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			body := base()
			body[tc.field] = tc.value
			r := alice.do("POST", "/api/activities", body)
			if r.Status != 400 || r.errCode() != "VALIDATION_ERROR" {
				t.Fatalf("got %d %v, want 400 VALIDATION_ERROR", r.Status, r.Body)
			}
		})
	}
	// Boundary paces from the spec are accepted.
	for _, pace := range []int{210, 285, 330, 375, 600} {
		body := base()
		body["paceSecondsPerKm"] = pace
		alice.must(201, "POST", "/api/activities", body)
	}
}

func TestMultipleActivitiesSameDay(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	alice.logRun("2026-10-04", 8)
	alice.logRun("2026-10-04", 5)
	alice.logRun("2026-10-04", 3)

	r := alice.must(200, "GET", "/api/stats/day?date=2026-10-04", nil)
	approx(t, "daily mileage", num(r.data()["distanceKm"]), 16)
	if n := len(r.data()["actual"].([]any)); n != 3 {
		t.Fatalf("got %d activities, want 3", n)
	}
	list := alice.must(200, "GET", "/api/activities", nil)
	if p := list.Body["pagination"].(map[string]any); num(p["total"]) != 3 {
		t.Fatalf("pagination total = %v, want 3", p["total"])
	}
}

func TestEditActivityKeepsLink(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "intervals", 10)
	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 10.2})
	actualID := r.data()["actualActivity"].(map[string]any)["id"].(string)

	upd := alice.must(200, "PUT", "/api/activities/"+actualID, map[string]any{
		"activityDate": "2026-10-04", "activityType": "intervals", "title": "Tuesday Intervals",
		"distanceKm": 10.5, "trainingActivityId": nil,
	}).data()
	if upd["trainingActivityId"] != planned {
		t.Fatalf("edit detached the activity: %v", upd)
	}
	if upd["plannedActivity"].(map[string]any)["id"] != planned {
		t.Fatalf("detail must include linked plan: %v", upd)
	}
}

func TestDeleteLinkedActivityRevertsPlan(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "easy_run", 8)
	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 8.4})
	actualID := r.data()["actualActivity"].(map[string]any)["id"].(string)

	alice.must(204, "DELETE", "/api/activities/"+actualID, nil)
	p := alice.must(200, "GET", "/api/training-activities/"+planned, nil).data()
	if p["status"] != "planned" {
		t.Fatalf("status = %v, want planned", p["status"])
	}
	if _, ok := p["actualActivity"]; ok {
		t.Fatalf("plan should have no result: %v", p)
	}
	alice.must(404, "GET", "/api/activities/"+actualID, nil)
}

// --------------------------------------------------- training activities ----

func TestTrainingBlockValidation(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	r := alice.do("POST", "/api/training-blocks", map[string]any{"name": "", "startDate": "2026-10-05", "endDate": "2027-01-17"})
	if r.Status != 400 {
		t.Fatalf("empty name: got %d", r.Status)
	}
	r = alice.do("POST", "/api/training-blocks", map[string]any{"name": "X", "startDate": "2026-10-05", "endDate": "2026-10-01"})
	if r.Status != 400 {
		t.Fatalf("end before start: got %d", r.Status)
	}
	block := alice.createBlock("2026-10-05", "2027-01-17")
	r = alice.do("POST", "/api/training-blocks/"+block+"/activities", map[string]any{
		"activityDate": "2027-02-01", "activityType": "easy_run",
	})
	if r.Status != 400 {
		t.Fatalf("planned date outside block: got %d", r.Status)
	}
	alice.createPlanned(block, "2026-12-01", "easy_run", 8)
	r = alice.do("PUT", "/api/training-blocks/"+block, map[string]any{
		"name": "Shorter", "startDate": "2026-10-05", "endDate": "2026-11-01",
	})
	if r.Status != 400 {
		t.Fatalf("shrinking block past planned activity: got %d", r.Status)
	}
}

func TestCreatePlannedActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-10-05", "2027-01-17")
	r := alice.must(201, "POST", "/api/training-blocks/"+block+"/activities", map[string]any{
		"activityDate": "2026-10-06", "activityType": "intervals", "title": "6 x 1 km",
		"description": "@ 4:30/km", "plannedDistanceKm": 10, "plannedPaceSecondsPerKm": 270,
	})
	d := r.data()
	if d["status"] != "planned" || d["trainingBlockId"] != block || num(d["plannedDistanceKm"]) != 10 {
		t.Fatalf("unexpected planned activity %v", d)
	}
	// Strength sessions don't need distance or pace; two on one day is fine.
	alice.createPlanned(block, "2026-10-06", "strength", 0)
	list := alice.must(200, "GET", "/api/training-blocks/"+block+"/activities", nil).list()
	if len(list) != 2 {
		t.Fatalf("got %d planned activities, want 2", len(list))
	}
	r = alice.do("POST", "/api/training-blocks/"+block+"/activities", map[string]any{
		"activityDate": "2026-10-07", "activityType": "easy_run", "plannedDistanceKm": -1,
	})
	if r.Status != 400 {
		t.Fatalf("negative planned distance: got %d", r.Status)
	}
}

func TestCompletePlannedActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "easy_run", 8)

	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{
		"distanceKm": 8.4, "paceSecondsPerKm": 338, "averageHeartRate": 151, "comment": "Felt strong today.",
	})
	d := r.data()
	if d["status"] != "completed" {
		t.Fatalf("status = %v", d["status"])
	}
	actual := d["actualActivity"].(map[string]any)
	if actual["trainingActivityId"] != planned || num(actual["distanceKm"]) != 8.4 || actual["activityType"] != "easy_run" {
		t.Fatalf("unexpected linked activity %v", actual)
	}

	// Completing again updates the same result rather than duplicating it.
	alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 8.6})
	day := alice.must(200, "GET", "/api/stats/day?date=2026-10-04", nil).data()
	approx(t, "daily mileage", num(day["distanceKm"]), 8.6)

	// Completing invalid metrics is rejected.
	if r := alice.do("POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 0}); r.Status != 400 {
		t.Fatalf("invalid completion: got %d", r.Status)
	}
	stats := alice.must(200, "GET", "/api/training-blocks/"+block+"/stats?today=2026-10-04", nil).data()
	summary := stats["summary"].(map[string]any)
	if num(summary["completed"]) != 1 || num(summary["completionPct"]) != 100 {
		t.Fatalf("block stats not updated: %v", summary)
	}
}

func TestSkipPlannedActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-03", "tempo", 10)

	if r := alice.do("POST", "/api/training-activities/"+planned+"/skip", map[string]any{}); r.Status != 400 {
		t.Fatalf("skip without reason: got %d", r.Status)
	}
	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/skip", map[string]any{
		"reason": "fatigue", "notes": "Legs were still tired.",
	})
	d := r.data()
	if d["status"] != "skipped" || d["completionReason"] != "fatigue" || d["completionNotes"] != "Legs were still tired." {
		t.Fatalf("unexpected skipped activity %v", d)
	}
	if _, ok := d["actualActivity"]; ok {
		t.Fatal("skipping must not create an actual activity")
	}
	list := alice.must(200, "GET", "/api/activities", nil).list()
	if len(list) != 0 {
		t.Fatalf("skipping created %d activities", len(list))
	}

	// Reset brings it back; completed plans can't be skipped silently.
	alice.must(200, "POST", "/api/training-activities/"+planned+"/reset", nil)
	alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 10})
	if r := alice.do("POST", "/api/training-activities/"+planned+"/skip", map[string]any{"reason": "work"}); r.Status != 409 {
		t.Fatalf("skip with logged result: got %d, want 409", r.Status)
	}
}

func TestModifyPlannedActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "tempo", 10)

	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/modify", map[string]any{
		"activityType": "easy_run", "distanceKm": 7, "paceSecondsPerKm": 355, "averageHeartRate": 152,
		"comment": "Tempo was too hard today, switched to easy running.",
	})
	d := r.data()
	if d["status"] != "modified" {
		t.Fatalf("status = %v", d["status"])
	}
	actual := d["actualActivity"].(map[string]any)
	if actual["activityType"] != "easy_run" || actual["title"] != "Easy Run" || actual["trainingActivityId"] != planned {
		t.Fatalf("unexpected modified result %v", actual)
	}
	// The plan itself is unchanged.
	if d["activityType"] != "tempo" || num(d["plannedDistanceKm"]) != 10 {
		t.Fatalf("plan was altered: %v", d)
	}
}

func TestLinkActivityThroughCreate(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "easy_run", 8)
	alice.must(201, "POST", "/api/activities", map[string]any{
		"trainingActivityId": planned, "activityDate": "2026-10-04", "activityType": "easy_run", "distanceKm": 8,
	})
	p := alice.must(200, "GET", "/api/training-activities/"+planned, nil).data()
	if p["status"] != "completed" {
		t.Fatalf("status = %v", p["status"])
	}
	r := alice.do("POST", "/api/activities", map[string]any{
		"trainingActivityId": planned, "activityDate": "2026-10-04", "activityType": "easy_run", "distanceKm": 8,
	})
	if r.Status != 409 {
		t.Fatalf("second link: got %d, want 409", r.Status)
	}
}

func TestDeletePlannedKeepsActualActivity(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "easy_run", 8)
	r := alice.must(200, "POST", "/api/training-activities/"+planned+"/complete", map[string]any{"distanceKm": 8})
	actualID := r.data()["actualActivity"].(map[string]any)["id"].(string)

	alice.must(204, "DELETE", "/api/training-blocks/"+block, nil)
	a := alice.must(200, "GET", "/api/activities/"+actualID, nil).data()
	if _, linked := a["trainingActivityId"]; linked {
		t.Fatalf("activity should now be unplanned: %v", a)
	}
}

// ------------------------------------------------------------ statistics ----

func TestMileageStatistics(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	// Today is Sunday 2026-10-04; the week is Mon 09-28 → Sun 10-04.
	alice.logRun("2026-10-04", 8.4)
	alice.logRun("2026-10-04", 4.2)
	alice.logRun("2026-09-28", 10) // Monday, same week, previous month
	alice.logRun("2026-09-27", 20) // Sunday of the previous week
	alice.logRun("2026-10-05", 6)  // next Monday: outside this week, inside the month
	alice.logRun("2026-01-15", 5)
	alice.logRun("2025-12-31", 7) // previous year

	day := alice.must(200, "GET", "/api/stats/day?date=2026-10-04", nil).data()
	approx(t, "daily", num(day["distanceKm"]), 12.6)

	weekly := alice.must(200, "GET", "/api/stats/weekly?date=2026-10-04", nil).data()
	approx(t, "weekly", num(weekly["totalDistanceKm"]), 22.6)
	pts := weekly["points"].([]any)
	if len(pts) != 7 || pts[0].(map[string]any)["date"] != "2026-09-28" || pts[6].(map[string]any)["date"] != "2026-10-04" {
		t.Fatalf("weekly buckets must be Monday→Sunday: %v", pts)
	}

	monthly := alice.must(200, "GET", "/api/stats/monthly?date=2026-10-04", nil).data()
	approx(t, "monthly", num(monthly["totalDistanceKm"]), 18.6)
	if n := len(monthly["points"].([]any)); n != 31 {
		t.Fatalf("october has %d points, want 31", n)
	}

	yearly := alice.must(200, "GET", "/api/stats/yearly?date=2026-10-04", nil).data()
	approx(t, "yearly", num(yearly["totalDistanceKm"]), 53.6)
	months := yearly["points"].([]any)
	if len(months) != 12 {
		t.Fatalf("yearly has %d points, want 12", len(months))
	}
	approx(t, "september", num(months[8].(map[string]any)["distanceKm"]), 30)

	ov := alice.must(200, "GET", "/api/stats/overview?today=2026-10-04", nil).data()
	totals := ov["totals"].(map[string]any)
	approx(t, "overview week", num(totals["week"].(map[string]any)["distanceKm"]), 22.6)
	approx(t, "overview month", num(totals["month"].(map[string]any)["distanceKm"]), 12.6) // up to today
	approx(t, "overview year", num(totals["year"].(map[string]any)["distanceKm"]), 47.6)
	approx(t, "overview all", num(totals["allTime"].(map[string]any)["distanceKm"]), 60.6)
	approx(t, "longest", num(ov["longestActivity"].(map[string]any)["distanceKm"]), 20)
}

func TestWeightedPace(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	alice.must(201, "POST", "/api/activities", map[string]any{
		"activityDate": "2026-10-01", "activityType": "tempo", "distanceKm": 5, "paceSecondsPerKm": 300})
	alice.must(201, "POST", "/api/activities", map[string]any{
		"activityDate": "2026-10-02", "activityType": "long_run", "distanceKm": 20, "paceSecondsPerKm": 360})
	ov := alice.must(200, "GET", "/api/stats/overview?today=2026-10-04&period=all", nil).data()
	// (5×300 + 20×360) / 25 = 348, not the naive (300+360)/2 = 330.
	approx(t, "weighted pace", num(ov["aggregates"].(map[string]any)["averagePaceSecondsPerKm"]), 348)
	approx(t, "fastest", num(ov["fastestActivity"].(map[string]any)["paceSecondsPerKm"]), 300)
}

func TestPlannedVsActual(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	block := alice.createBlock("2026-09-28", "2026-10-11")
	// Week 1 (09-28 → 10-04).
	mon := alice.createPlanned(block, "2026-09-28", "easy_run", 8)
	tue := alice.createPlanned(block, "2026-09-29", "intervals", 10)
	alice.createPlanned(block, "2026-09-30", "rest", 0)
	thu := alice.createPlanned(block, "2026-10-01", "tempo", 8)
	alice.createPlanned(block, "2026-10-03", "long_run", 24)
	// Week 2, still in the future relative to "today".
	alice.createPlanned(block, "2026-10-06", "easy_run", 10)

	alice.must(200, "POST", "/api/training-activities/"+mon+"/complete", map[string]any{"distanceKm": 8.2})
	alice.must(200, "POST", "/api/training-activities/"+tue+"/modify", map[string]any{"activityType": "easy_run", "distanceKm": 7})
	alice.must(200, "POST", "/api/training-activities/"+thu+"/skip", map[string]any{"reason": "illness"})
	alice.logRun("2026-10-04", 5) // unplanned run within the block

	week := alice.must(200, "GET", "/api/stats/week?date=2026-10-01&today=2026-10-04", nil).data()
	pva := week["plannedVsActual"].(map[string]any)
	// Planned excludes skipped: 8 + 10 + 24 = 42. Actual: 8.2 + 7 + 5 = 20.2.
	approx(t, "planned", num(pva["plannedDistanceKm"]), 42)
	approx(t, "actual", num(pva["actualDistanceKm"]), 20.2)
	approx(t, "difference", num(pva["differenceKm"]), 20.2-42)
	// Due non-rest: mon, tue, thu, sat = 4; done: mon + tue (modified) = 2.
	approx(t, "completion", num(pva["completionPct"]), 50)

	stats := alice.must(200, "GET", "/api/training-blocks/"+block+"/stats?today=2026-10-04", nil).data()
	s := stats["summary"].(map[string]any)
	if num(s["completed"]) != 1 || num(s["modified"]) != 1 || num(s["skipped"]) != 1 || num(s["planned"]) != 2 {
		t.Fatalf("unexpected block summary %v", s)
	}
	approx(t, "block planned km", num(s["plannedDistanceKm"]), 52)
	approx(t, "block actual km", num(s["actualDistanceKm"]), 20.2)
	approx(t, "block completion", num(s["completionPct"]), 50) // future session not counted
	weeks := stats["weeks"].([]any)
	if len(weeks) != 2 {
		t.Fatalf("got %d weeks, want 2", len(weeks))
	}
	approx(t, "week 2 planned", num(weeks[1].(map[string]any)["plannedDistanceKm"]), 10)

	blocks := alice.must(200, "GET", "/api/training-blocks?today=2026-10-04", nil).list()
	if len(blocks) != 1 {
		t.Fatalf("got %d blocks", len(blocks))
	}
	approx(t, "list completion", num(blocks[0].(map[string]any)["summary"].(map[string]any)["completionPct"]), 50)
}

// -------------------------------------------------------- data isolation ----

func TestDataIsolation(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	bob := e.user("bob")

	block := alice.createBlock("2026-09-28", "2026-10-25")
	planned := alice.createPlanned(block, "2026-10-04", "easy_run", 8)
	run := alice.logRun("2026-10-03", 12)

	type attempt struct {
		method, path string
		body         any
	}
	attempts := []attempt{
		// Training blocks.
		{"GET", "/api/training-blocks/" + block, nil},
		{"PUT", "/api/training-blocks/" + block, map[string]any{"name": "Hacked", "startDate": "2026-09-28", "endDate": "2026-10-25"}},
		{"DELETE", "/api/training-blocks/" + block, nil},
		{"GET", "/api/training-blocks/" + block + "/stats", nil},
		{"GET", "/api/training-blocks/" + block + "/activities", nil},
		{"POST", "/api/training-blocks/" + block + "/activities", map[string]any{"activityDate": "2026-10-05", "activityType": "easy_run"}},
		// Planned activities.
		{"GET", "/api/training-activities/" + planned, nil},
		{"PUT", "/api/training-activities/" + planned, map[string]any{"activityDate": "2026-10-04", "activityType": "rest"}},
		{"POST", "/api/training-activities/" + planned + "/complete", map[string]any{"distanceKm": 1}},
		{"POST", "/api/training-activities/" + planned + "/modify", map[string]any{"distanceKm": 1}},
		{"POST", "/api/training-activities/" + planned + "/skip", map[string]any{"reason": "work"}},
		{"POST", "/api/training-activities/" + planned + "/reset", nil},
		{"DELETE", "/api/training-activities/" + planned, nil},
		// Actual activities.
		{"GET", "/api/activities/" + run, nil},
		{"PUT", "/api/activities/" + run, map[string]any{"activityDate": "2026-10-03", "activityType": "easy_run", "distanceKm": 1}},
		{"DELETE", "/api/activities/" + run, nil},
	}
	for _, a := range attempts {
		r := bob.do(a.method, a.path, a.body)
		if r.Status != 404 && r.Status != 403 {
			t.Errorf("bob %s %s: got %d %v, want 404/403", a.method, a.path, r.Status, r.Body)
		}
		if r.data() != nil || r.list() != nil {
			t.Errorf("bob %s %s leaked data: %v", a.method, a.path, r.Body)
		}
	}

	// Bob cannot link his activity to Alice's planned activity.
	r := bob.do("POST", "/api/activities", map[string]any{
		"trainingActivityId": planned, "activityDate": "2026-10-04", "activityType": "easy_run", "distanceKm": 8,
	})
	if r.Status < 400 || r.Status >= 500 {
		t.Errorf("bob linking to alice's plan: got %d, want rejection", r.Status)
	}

	// Bob's lists and stats contain nothing of Alice's.
	if n := len(bob.must(200, "GET", "/api/training-blocks", nil).list()); n != 0 {
		t.Errorf("bob sees %d blocks", n)
	}
	if n := len(bob.must(200, "GET", "/api/activities", nil).list()); n != 0 {
		t.Errorf("bob sees %d activities", n)
	}
	day := bob.must(200, "GET", "/api/stats/day?date=2026-10-04", nil).data()
	if len(day["planned"].([]any)) != 0 || num(day["distanceKm"]) != 0 {
		t.Errorf("bob's day view leaked alice's data: %v", day)
	}
	ov := bob.must(200, "GET", "/api/stats/overview?today=2026-10-04", nil).data()
	if num(ov["totals"].(map[string]any)["allTime"].(map[string]any)["distanceKm"]) != 0 {
		t.Errorf("bob's overview includes alice's mileage")
	}

	// Alice's data is untouched.
	b := alice.must(200, "GET", "/api/training-blocks/"+block, nil).data()
	if b["name"] != "Marathon Block" {
		t.Errorf("alice's block was modified: %v", b)
	}
	p := alice.must(200, "GET", "/api/training-activities/"+planned, nil).data()
	if p["status"] != "planned" {
		t.Errorf("alice's plan was modified: %v", p)
	}
	a := alice.must(200, "GET", "/api/activities/"+run, nil).data()
	approx(t, "alice run distance", num(a["distanceKm"]), 12)
}

func TestMalformedIDsReturn404(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	for _, p := range []string{"/api/activities/123", "/api/training-blocks/abc", "/api/training-activities/' OR 1=1 --"} {
		if r := alice.do("GET", p, nil); r.Status != 404 {
			t.Errorf("GET %s: got %d", p, r.Status)
		}
	}
	if r := alice.do("GET", fmt.Sprintf("/api/activities/%s", "00000000-0000-0000-0000-000000000000"), nil); r.Status != 404 {
		t.Errorf("unknown uuid: got %d", r.Status)
	}
}

func TestErrorsDoNotLeakInternals(t *testing.T) {
	e := setup(t)
	alice := e.user("alice")
	r := alice.do("POST", "/api/activities", "not an object")
	if r.Status != 400 {
		t.Fatalf("got %d", r.Status)
	}
	msg := r.Body["error"].(map[string]any)["message"].(string)
	for _, bad := range []string{"SQLSTATE", "pgx", "json:", "goroutine"} {
		if bytes.Contains([]byte(msg), []byte(bad)) {
			t.Fatalf("error message leaks internals: %q", msg)
		}
	}
}

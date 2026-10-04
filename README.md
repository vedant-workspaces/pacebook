# Pacebook

**Plan your training. Run your plan. Track what actually happened.**

Pacebook is a personal running log and training diary. It keeps two things side by side:

- **The plan:** training blocks with planned sessions on specific dates.
- **The reality:** the activities you actually ran, with distance, pace, heart rate and a comment.

From those two it shows weekly, monthly and yearly mileage, planned vs actual comparisons, completion rates and training trends.

---

## Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Folder structure](#folder-structure)
- [Database schema](#database-schema)
- [Authentication](#authentication)
- [Google OAuth setup](#google-oauth-setup)
- [Environment variables](#environment-variables)
- [Local development](#local-development)
- [Database setup & migrations](#database-setup--migrations)
- [API documentation](#api-documentation)
- [Statistics definitions](#statistics-definitions)
- [Testing](#testing)
- [Production deployment](#production-deployment)
- [Future roadmap](#future-roadmap)

---

## Overview

| Feature | Where |
|---|---|
| Sign in with Google (plus an optional development sign-in) | `/login` |
| Dashboard: today's plan with one-tap **Complete**, today's activities and their total, week/month/year mileage, planned vs actual for the week, mileage chart | `/` |
| Training blocks: list with completion %, planned and actual km | `/training` |
| Create and edit a block (name, dates, goal, description) | `/training/new`, `/training/:id/edit` |
| Block detail: week and month calendar, add sessions, day view (plan vs actual), complete / not completed / modified flows, block statistics, weekly planned vs actual chart and table | `/training/:id` |
| Log an activity, planned or not; several per day are allowed | `/log` |
| Run history, paginated, filterable by type, as a table on desktop and cards on mobile | `/runs` |
| Activity detail with its linked plan, edit, delete | `/runs/:id`, `/runs/:id/edit` |
| Statistics: mileage, counts, average distance, weighted average pace, average heart rate, longest activity, fastest pace, planned vs actual, completion | `/stats` |

### Planned vs actual

- A **planned activity** (`training_activities`) is what you intended to do. Its status is `planned`, `completed`, `modified` or `skipped`.
- An **actual activity** (`activities`) is what you did. `training_activity_id` links it to a plan, or is `NULL` for an unplanned run.
- **Complete** creates the linked actual activity and sets the plan to `completed`.
- **Completed differently** does the same but sets `modified`, and lets you record a different activity type (for example, planned tempo, ran easy).
- **Mark not completed** sets `skipped` with a reason (Injury, Illness, Fatigue, Work, Travel, Weather, Lack of Time, Recovery, Personal, Other) and optional notes. No actual activity is created.
- **Deleting a linked actual activity** keeps the planned activity and returns it to `planned`.
- **Deleting a planned activity or a block** keeps any linked actual activities. They become unplanned, so no mileage is lost.

---

## Architecture

```
React + TypeScript (Vite)          browser; all dates are the runner's local dates
          │  fetch /api/* (cookie session, X-Requested-With header)
          ▼
Go REST API (net/http)
  handlers    → parse HTTP, call services, shape {data}/{error} envelopes
  middleware  → recover, logging, security headers, CORS, CSRF, auth
  services    → validation and business rules (complete/skip/modify, stats)
  repository  → all SQL, always scoped by user_id
  auth        → Google OAuth (state + PKCE), HMAC-hashed DB sessions
          │  pgx
          ▼
PostgreSQL 15+     embedded SQL migrations, applied on start-up
```

In development, Vite proxies `/api` to the Go server, so cookies are same-origin. In production the Go binary can serve the built frontend itself (`STATIC_DIR`), giving a single deployable service.

---

## Tech stack

- **Frontend:** React 19, TypeScript, Vite, React Router, Tailwind CSS v4, Recharts. No other runtime dependencies.
- **Backend:** Go 1.24, standard-library `net/http` routing, `pgx/v5`, `golang.org/x/oauth2`.
- **Database:** PostgreSQL 15 or later. Migrations use `ON DELETE SET NULL (column)`, which needs 15+.
- **Tests:** Go `testing` with HTTP integration tests against a real PostgreSQL database, and Vitest for the frontend utilities.

---

## Folder structure

```
pacebook/
├── backend/
│   ├── cmd/
│   │   ├── server/main.go        # API entry point (config, DB, migrate, serve)
│   │   └── seed/main.go          # optional development seed
│   ├── config/                   # environment variable loading/validation
│   ├── internal/
│   │   ├── auth/                 # Google OAuth flow, session cookies
│   │   ├── database/             # pool + migration runner
│   │   ├── handlers/             # HTTP handlers, router, integration tests
│   │   ├── httpx/                # JSON response/error envelope
│   │   ├── middleware/           # auth, CORS, CSRF, logging, recover
│   │   ├── models/               # domain types, activity type catalogue, Date
│   │   ├── repository/           # SQL (user-scoped), statistics aggregation
│   │   └── services/             # validation + business logic
│   ├── migrations/               # 001_…sql – 006_…sql (embedded)
│   └── go.mod
├── frontend/
│   └── src/
│       ├── components/{layout,dashboard,training,activities,charts,ui}/
│       ├── pages/                # Login, Dashboard, Training, TrainingBlock, …
│       ├── services/api.ts       # typed API client
│       ├── hooks/                # useAuth, useMeta, useAsync
│       ├── types/                # TypeScript models
│       └── utils/                # pace/distance formatting, local-date maths
├── docker-compose.yml            # local PostgreSQL
├── Makefile
└── .env.example
```

---

## Database schema

```
users ─┬─< training_blocks ─< training_activities (planned)
       │                              │ 0..1
       ├─< activities (actual) >──────┘  training_activity_id NULL = unplanned
       └─< sessions
```

| Table | Key columns |
|---|---|
| `users` | `id` UUID, `google_id` (unique), `email` (unique), `name`, `profile_picture`, timestamps |
| `training_blocks` | `id`, `user_id`, `name`, `description`, `start_date` DATE, `end_date` DATE, `goal`, timestamps; `CHECK end_date >= start_date` |
| `training_activities` | `id`, `training_block_id`, `user_id`, `activity_date` DATE, `activity_type`, `title`, `description`, `planned_distance_km`, `planned_pace_seconds_per_km`, `planned_duration_seconds`, `status`, `completion_reason`, `completion_notes`, timestamps |
| `activities` | `id`, `user_id`, `training_activity_id` (nullable), `activity_date` DATE, `activity_type`, `title`, `distance_km`, `pace_seconds_per_km`, `average_heart_rate`, `comment`, `source` (default `manual`), `external_id`, timestamps |
| `sessions` | `id`, `user_id`, `token_hash` (HMAC-SHA256, unique), `expires_at` |

Design notes:

- **Many activities per day.** There is no uniqueness on `(user_id, activity_date)` in either table.
- **One result per plan.** A partial unique index on `activities(training_activity_id)` allows at most one linked actual activity per planned activity.
- **Ownership enforced by the database too.** Child rows reference `(id, user_id)` composite keys, so a planned activity cannot belong to another user's block, and an actual activity cannot link to another user's plan.
- **Dates are `DATE`**, not timestamps, so a run on your local Sunday never shifts to Saturday through UTC.
- **Pace is stored as integer seconds per km** (330 means 5:30/km).
- **`source` and `external_id`** are reserved for future Strava, Garmin or COROS imports. There is a unique index on `(user_id, source, external_id)`.
- **Indexes:** `activities(user_id, activity_date)`, `training_activities(user_id, activity_date)`, `training_blocks(user_id, start_date, end_date)`, `training_activities(training_block_id, activity_date)`, and `sessions(user_id)` / `(expires_at)`.

---

## Authentication

- `GET /api/auth/google` creates a random `state` and a PKCE verifier, stores both in short-lived HttpOnly cookies, and redirects to Google.
- `GET /api/auth/google/callback` checks `state` in constant time, exchanges the code with PKCE, and fetches the OpenID userinfo. It requires `email_verified`, upserts the user keyed on Google's subject ID, and creates a session.
- **Sessions** are 32-byte random tokens in a `pacebook_session` cookie: `HttpOnly`, `SameSite=Lax`, and `Secure` when `APP_ENV=production`. The database stores only an HMAC-SHA256 of the token, keyed with `SESSION_SECRET`. Expired sessions are cleaned up hourly.
- `POST /api/auth/logout` deletes the session row and clears the cookie. `GET /api/auth/me` returns the current user.
- **CSRF:** every state-changing request needs the `X-Requested-With` header, and any `Origin` header must be an allowed origin. A cross-site page cannot send that header without a CORS preflight, and preflights only succeed for configured origins. This works together with `SameSite=Lax`.
- **CORS** allows only `FRONTEND_URL` plus `CORS_ALLOWED_ORIGINS`, with credentials. Wildcards are never used.
- **User identity always comes from the session.** No endpoint accepts a user ID from the client.
- **Development sign-in** (`POST /api/auth/dev-login`) is only routed when `APP_ENV=development` **and** `DEV_AUTH_ENABLED=true`. The server refuses to start if it is enabled in production.

### Data isolation

Every repository query on user-owned data includes `AND user_id = $n` with the session user. Related IDs from request bodies, such as `trainingActivityId`, are re-checked for ownership. Another user's row looks exactly like a missing one, so the API returns **404 Not Found** whether the ID doesn't exist or belongs to someone else. Malformed IDs also return 404.

---

## Google OAuth setup

1. In the [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create or choose a project.
2. **OAuth consent screen:** set it to External, add your app name, and add the scopes `openid`, `email` and `profile`. Add yourself as a test user while the app is in testing.
3. **Credentials → Create credentials → OAuth client ID → Web application.**
   - Authorised JavaScript origins: `http://localhost:5173` (and your production origin).
   - Authorised redirect URIs: `http://localhost:8080/api/auth/google/callback` (and `https://your-domain/api/auth/google/callback`).
4. Put the client ID and secret in `.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, and set `GOOGLE_REDIRECT_URL` to the exact redirect URI.

Browser cookies are not port-specific, so in development the state cookie set via `localhost:5173` is still sent to the callback on `localhost:8080`.

---

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `APP_ENV` | no | `development` | `production` turns on Secure cookies and strict config checks |
| `PORT` | no | `8080` | API port |
| `DATABASE_URL` | **yes** | | PostgreSQL connection string |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | in prod | | OAuth client |
| `GOOGLE_REDIRECT_URL` | no | `<FRONTEND_URL>/api/auth/google/callback` | Must exactly match the redirect URI registered with Google |
| `SESSION_SECRET` | in prod (≥ 32 chars) | random per start in dev | HMAC key for session tokens |
| `SESSION_TTL_DAYS` | no | `30` | Session lifetime |
| `FRONTEND_URL` | no | `$RENDER_EXTERNAL_URL`, else `http://localhost:5173` | CORS origin and post-login redirect |
| `CORS_ALLOWED_ORIGINS` | no | | Extra origins, comma-separated |
| `DEV_AUTH_ENABLED` | no | `false` | Development sign-in (development only) |
| `STATIC_DIR` | no | | Serve the built SPA from this directory |
| `TEST_DATABASE_URL` | tests | | Database used and **truncated** by integration tests |

Copy `.env.example` to `.env`. `.env` is git-ignored.

---

## Local development

Prerequisites: Go 1.24+, Node 20+, and PostgreSQL 15+ (or Docker).

```bash
# 1. Database (creates `pacebook` and `pacebook_test`)
docker compose up -d db

# 2. Configuration
cp .env.example .env
#   either fill in GOOGLE_* or set DEV_AUTH_ENABLED=true for local sign-in

# 3. API on :8080 (runs migrations automatically)
make dev-api          # = cd backend && go run ./cmd/server

# 4. Frontend on :5173 (another terminal)
cd frontend && npm install && npm run dev

# 5. Optional sample data (a 16-week block, results, skips, unplanned runs)
make seed EMAIL=runner@pacebook.local
```

Open http://localhost:5173. With `DEV_AUTH_ENABLED=true`, use **Development sign-in** with the same email you seeded.

---

## Database setup & migrations

Migrations are plain SQL files in `backend/migrations/`, embedded in the binary:

```
001_create_users.sql
002_create_training_blocks.sql
003_create_training_activities.sql
004_create_activities.sql
005_add_indexes.sql
006_create_sessions.sql
```

On start-up the server applies any that haven't run yet, in order, each in its own transaction. Applied versions are recorded in `schema_migrations`, and a Postgres advisory lock stops concurrent processes from migrating at the same time. A fresh, empty database is all that's needed. To add a migration, create `007_description.sql`; never edit one that has already been applied.

The seed command (`backend/cmd/seed`) refuses to run when `APP_ENV=production`.

---

## API documentation

Every endpoint except `/api/health` and the `auth` routes requires a session cookie. All writes need the header `X-Requested-With: pacebook`.

**Responses**

```jsonc
{ "data": { … } }                                        // success
{ "data": [ … ], "pagination": { "page": 1, "limit": 20, "total": 100 } }
{ "error": { "code": "VALIDATION_ERROR", "message": "Distance must be greater than zero", "field": "distanceKm" } }
```

Error codes: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403, CSRF), `NOT_FOUND` (404), `CONFLICT` (409), `INTERNAL_ERROR` (500). Internal and database errors are logged on the server and never returned to the client.

Dates are `YYYY-MM-DD`. Statistics endpoints take `today=YYYY-MM-DD` (the client's local date), so weeks, months and years follow the runner's calendar.

### Auth & meta
| Method | Path | Notes |
|---|---|---|
| GET | `/api/auth/google` | Start Google sign-in |
| GET | `/api/auth/google/callback` | OAuth callback |
| POST | `/api/auth/logout` | |
| GET | `/api/auth/me` | Current user |
| GET | `/api/auth/config` | `{googleEnabled, devLoginEnabled}` |
| POST | `/api/auth/dev-login` | Development only: `{email, name}` |
| GET | `/api/meta` | Activity types and skip reasons (the UI reads these, so new types only need adding on the server) |

### Training blocks
| Method | Path | Body / query |
|---|---|---|
| GET | `/api/training-blocks` | `?page&limit&today`; each block includes a `summary` |
| POST | `/api/training-blocks` | `{name, description?, startDate, endDate, goal?}` |
| GET / PUT / DELETE | `/api/training-blocks/:id` | PUT is rejected if planned activities would fall outside the new dates |
| GET | `/api/training-blocks/:id/stats` | Counts, completion, planned/actual km, longest run, averages, `weeks[]` |
| GET | `/api/training-blocks/:id/activities` | `?from&to` |
| POST | `/api/training-blocks/:id/activities` | `{activityDate, activityType, title?, description?, plannedDistanceKm?, plannedPaceSecondsPerKm?, plannedDurationSeconds?}` |

### Planned activities
| Method | Path | Body |
|---|---|---|
| GET / PUT / DELETE | `/api/training-activities/:id` | GET includes `actualActivity` |
| POST | `/api/training-activities/:id/complete` | `{distanceKm?, paceSecondsPerKm?, averageHeartRate?, comment?, activityDate?}` |
| POST | `/api/training-activities/:id/modify` | Same, plus `activityType?` and `title?` |
| POST | `/api/training-activities/:id/skip` | `{reason, notes?}`; 409 if a result is already logged |
| POST | `/api/training-activities/:id/reset` | Back to `planned` (no logged result allowed) |

Calling complete or modify again updates the existing linked result rather than duplicating it.

### Actual activities
| Method | Path | Body / query |
|---|---|---|
| GET | `/api/activities` | `?page&limit&from&to&type`, newest first |
| POST | `/api/activities` | `{activityDate, activityType, title?, distanceKm?, paceSecondsPerKm?, averageHeartRate?, comment?, trainingActivityId?}` |
| GET / PUT / DELETE | `/api/activities/:id` | PUT never changes the link to a plan. DELETE returns a linked plan to `planned` |

Validation: distance > 0 and < 500 km; pace 2:00–30:00 per km (120–1800 s); heart rate 30–250 bpm; comment ≤ 2000 characters; title ≤ 120; valid dates. Planned distance must be ≥ 0, and planned distance, pace and duration are all optional.

### Statistics
| Method | Path | Returns |
|---|---|---|
| GET | `/api/stats/overview?period=week\|month\|year\|all&today=` | Week/month/year/all-time totals; aggregates, records and plan comparison for the period |
| GET | `/api/stats/weekly?date=` | Daily actual (and planned) km for the Monday–Sunday week |
| GET | `/api/stats/monthly?date=` | Daily km for the calendar month |
| GET | `/api/stats/yearly?date=` | Monthly km for the calendar year |
| GET | `/api/stats/day?date=` | `{planned[], actual[], distanceKm}` |
| GET | `/api/stats/week?date=&today=` | Daily km, planned sessions, planned vs actual, completion |

---

## Statistics definitions

- **Week:** Monday to Sunday, everywhere (dashboard, statistics, calendar, blocks).
- **Mileage** is always `SUM(distance_km)` of **actual** activities, including unplanned ones. Planned mileage is computed separately and never mixed in. Everything is aggregated in SQL.
- **Planned mileage** is `SUM(planned_distance_km)` of planned activities whose status is not `skipped`. On the statistics page, month, year and all-time compare against the plan **up to today**, so future sessions don't inflate the plan. Weeks and blocks use their full plan.
- **Training completion (v1):**

  ```
  completed + modified
  ───────────────────────────────────────────────── × 100
  non-rest planned activities that are "due"
  ```

  A session is *due* once its date is today or earlier, or once it has been marked completed, modified or skipped. Rest days never count, and future sessions don't count against you. `modified` counts as done because the runner trained, just differently.
- **Average pace** is weighted: `Σ(distance × pace) / Σ distance`, over activities that have both a distance and a pace. It is never the mean of individual paces. For example, 5 km @ 5:00 plus 20 km @ 6:00 gives 5:48/km, not 5:30.
- **Fastest pace** considers activities of at least 1 km.
- **Block actual mileage** includes every actual activity dated inside the block, unplanned ones included.

---

## Testing

```bash
make test        # both suites
make test-api    # go vet + go test (needs TEST_DATABASE_URL; it gets TRUNCATEd)
make test-web    # tsc + vitest
```

Backend integration tests (`backend/internal/handlers/api_test.go`) run the full router against PostgreSQL. They cover:

- **Auth:** 401 without a session, access with one, logout, the CSRF header and Origin checks, dev login not routed unless enabled.
- **Activities:** a valid activity; invalid distance, pace, heart rate, date, type and comment; paces 3:30, 4:45, 5:30, 6:15 and 10:00 accepted; three activities on one day totalling 16 km; edits keep the link; deleting a linked run reverts the plan.
- **Training:** block validation, creating planned activities (including two on one day), complete, skip (reason required, no actual activity created, 409 after a result is logged), modify, linking through `POST /api/activities`, deleting a block keeps its runs.
- **Statistics:** daily, weekly (Monday–Sunday buckets), monthly, yearly and all-time mileage; weighted pace; planned vs actual; completion; block weekly comparison.
- **Data isolation:** user B gets 404 for reading, updating, deleting, completing, skipping, modifying or resetting any of user A's blocks, planned activities or actual activities. B cannot link to A's plan, and none of B's lists or stats include A's data.

Frontend unit tests cover pace formatting and parsing, distance formatting, and local-date maths (Monday weeks, DST-safe day arithmetic).

---

## Production deployment

### One-click: Render (recommended)

The repo includes a `Dockerfile` (one ~25 MB image: the Go API serving the built React app) and a `render.yaml` Blueprint for the web service. The database is hosted separately, for example on Neon's free tier, which doesn't expire.

1. **Database (Neon):** at [neon.tech](https://neon.tech) create a project with Postgres 16 or 17, in the region closest to your Render region. Copy the **direct** connection string; turn **off** "Connection pooling" so the host has no `-pooler`. It looks like `postgresql://user:pass@ep-xxx.region.aws.neon.tech/neondb?sslmode=require`.
2. **Google OAuth client:** create a *Web application* client (see [Google OAuth setup](#google-oauth-setup)). Use the redirect URI `https://<service-name>.onrender.com/api/auth/google/callback`.
3. On [render.com](https://render.com): **New → Blueprint**, connect GitHub, and pick this repository and branch. Enter `DATABASE_URL`, `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` when prompted, then click **Apply**.

Render generates `SESSION_SECRET`. `FRONTEND_URL` defaults to the service's public URL, and the OAuth redirect defaults to `<FRONTEND_URL>/api/auth/google/callback`. The tables are created automatically on first start. Every push redeploys.

The free web service sleeps when idle (about a 30-second cold start); the `starter` plan avoids that. For a custom domain, set `FRONTEND_URL=https://your-domain` and add `https://your-domain/api/auth/google/callback` to the Google client.

### Any Docker host (Fly.io, Railway, Cloud Run, a VPS)

```bash
docker build -t pacebook .
docker run -p 8080:8080 \
  -e DATABASE_URL=postgres://… -e SESSION_SECRET=$(openssl rand -hex 32) \
  -e FRONTEND_URL=https://your-domain \
  -e GOOGLE_CLIENT_ID=… -e GOOGLE_CLIENT_SECRET=… pacebook
```

The container listens on `$PORT` (default 8080) and needs HTTPS in front of it, because session cookies are `Secure`.

### Manual

1. Provision PostgreSQL 15+ and set `DATABASE_URL`.
2. Set `APP_ENV=production`, `SESSION_SECRET` (`openssl rand -hex 32`), `GOOGLE_*` with the production redirect URI, and `FRONTEND_URL=https://your-domain`.
3. Build: `make build` produces `backend/bin/pacebook` and `frontend/dist`.
4. **Single service (recommended):** run `STATIC_DIR=frontend/dist ./backend/bin/pacebook`. The API serves the SPA with client-side route fallback, so the frontend and API share an origin.
5. Put it behind HTTPS (a load balancer or reverse proxy). Session cookies are `Secure` in production and need HTTPS.
6. Migrations run automatically at start-up. `GET /api/health` checks database connectivity and suits load-balancer health checks.

To host the frontend separately (for example on a CDN), add its origin to `FRONTEND_URL` or `CORS_ALLOWED_ORIGINS` and route `/api/*` on that origin to the API. Same-site hosting keeps `SameSite=Lax` cookies working.

---

## Future roadmap

Not in v1 by design. The schema and code leave room for each:

1. **Integrations:** Strava, Garmin, COROS, Apple Health and Google Fit imports using `activities.source` and `external_id`.
2. **Richer metrics:** elevation, cadence, calories, RPE, weather and temperature as nullable columns on `activities`.
3. **Workout structure:** warm-up, intervals, recovery and cool-down segments, splits and laps as a child table of planned and actual activities.
4. **Goals:** weekly, monthly and yearly mileage goals, race and pace goals.
5. **Race tracking:** `activity_type = race` plus race metadata (distance class, official time, placing).
6. **Training load:** acute and chronic load, fitness and fatigue, then race prediction.
7. **Dark mode**, a PWA with offline logging, plan templates and copying weeks.
8. **AI running coach** that analyses plan vs actual, consistency and missed sessions.

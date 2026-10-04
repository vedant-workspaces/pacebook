-- Actual activities: what the runner really did. Many rows may share the same
-- (user_id, activity_date); there is deliberately no uniqueness on that pair.
CREATE TABLE activities (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- NULL for unplanned/spontaneous activities.
    training_activity_id UUID,
    activity_date        DATE NOT NULL,
    activity_type        TEXT NOT NULL,
    title                TEXT NOT NULL,
    distance_km          NUMERIC(7, 3),
    pace_seconds_per_km  INTEGER,
    average_heart_rate   INTEGER,
    comment              TEXT,
    -- Reserved for future integrations (strava, garmin, coros, ...).
    source               TEXT NOT NULL DEFAULT 'manual',
    external_id          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Deleting a planned activity keeps the run but turns it into an
    -- unplanned one (only training_activity_id is nulled; requires PG 15+).
    CONSTRAINT activities_training_activity_owner_fkey
        FOREIGN KEY (training_activity_id, user_id)
        REFERENCES training_activities(id, user_id) ON DELETE SET NULL (training_activity_id),
    CONSTRAINT activities_distance_check
        CHECK (distance_km IS NULL OR (distance_km > 0 AND distance_km < 500)),
    CONSTRAINT activities_pace_check
        CHECK (pace_seconds_per_km IS NULL OR pace_seconds_per_km > 0),
    CONSTRAINT activities_heart_rate_check
        CHECK (average_heart_rate IS NULL OR average_heart_rate BETWEEN 30 AND 250)
);

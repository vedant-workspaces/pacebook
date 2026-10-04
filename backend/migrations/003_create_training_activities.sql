CREATE TABLE training_activities (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_block_id           UUID NOT NULL,
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_date               DATE NOT NULL,
    activity_type               TEXT NOT NULL,
    title                       TEXT NOT NULL,
    description                 TEXT,
    planned_distance_km         NUMERIC(7, 3),
    planned_pace_seconds_per_km INTEGER,
    planned_duration_seconds    INTEGER,
    status                      TEXT NOT NULL DEFAULT 'planned',
    completion_reason           TEXT,
    completion_notes            TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT training_activities_block_owner_fkey
        FOREIGN KEY (training_block_id, user_id)
        REFERENCES training_blocks(id, user_id) ON DELETE CASCADE,
    CONSTRAINT training_activities_status_check
        CHECK (status IN ('planned', 'completed', 'skipped', 'modified')),
    CONSTRAINT training_activities_distance_check
        CHECK (planned_distance_km IS NULL OR planned_distance_km >= 0),
    CONSTRAINT training_activities_pace_check
        CHECK (planned_pace_seconds_per_km IS NULL OR planned_pace_seconds_per_km > 0),
    CONSTRAINT training_activities_duration_check
        CHECK (planned_duration_seconds IS NULL OR planned_duration_seconds > 0),
    CONSTRAINT training_activities_id_user_key UNIQUE (id, user_id)
);

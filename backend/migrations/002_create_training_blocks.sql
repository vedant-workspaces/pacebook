CREATE TABLE training_blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    goal        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT training_blocks_dates_check CHECK (end_date >= start_date),
    -- Lets child tables reference (id, user_id) so a row can never point at a
    -- block owned by a different user.
    CONSTRAINT training_blocks_id_user_key UNIQUE (id, user_id)
);

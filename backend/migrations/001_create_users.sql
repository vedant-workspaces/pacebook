CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id       TEXT NOT NULL,
    email           TEXT NOT NULL,
    name            TEXT NOT NULL DEFAULT '',
    profile_picture TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_google_id_key UNIQUE (google_id),
    CONSTRAINT users_email_key UNIQUE (email)
);

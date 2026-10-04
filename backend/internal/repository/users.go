package repository

import (
	"context"
	"time"

	"github.com/vedant-workspaces/pacebook/backend/internal/models"
)

const userColumns = `id, google_id, email, name, profile_picture, created_at, updated_at`

func scanUser(row interface{ Scan(...any) error }) (*models.User, error) {
	var u models.User
	err := row.Scan(&u.ID, &u.GoogleID, &u.Email, &u.Name, &u.ProfilePicture, &u.CreatedAt, &u.UpdatedAt)
	if err != nil {
		return nil, notFound(err)
	}
	return &u, nil
}

// UpsertGoogleUser creates the user on first sign-in, or refreshes their
// profile details on later sign-ins. Identity is keyed on the Google subject.
func (s *Store) UpsertGoogleUser(ctx context.Context, googleID, email, name string, picture *string) (*models.User, error) {
	row := s.db.QueryRow(ctx, `
		INSERT INTO users (google_id, email, name, profile_picture)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (google_id) DO UPDATE
		   SET email = EXCLUDED.email,
		       name = EXCLUDED.name,
		       profile_picture = EXCLUDED.profile_picture,
		       updated_at = now()
		RETURNING `+userColumns, googleID, email, name, picture)
	u, err := scanUser(row)
	if isUniqueViolation(err) {
		return nil, ErrConflict
	}
	return u, err
}

func (s *Store) GetUser(ctx context.Context, id string) (*models.User, error) {
	return scanUser(s.db.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1`, id))
}

func (s *Store) CreateSession(ctx context.Context, userID string, tokenHash []byte, expiresAt time.Time) error {
	_, err := s.db.Exec(ctx,
		`INSERT INTO sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
		userID, tokenHash, expiresAt)
	return err
}

// UserForSession returns the user owning an unexpired session.
func (s *Store) UserForSession(ctx context.Context, tokenHash []byte) (*models.User, error) {
	return scanUser(s.db.QueryRow(ctx, `
		SELECT u.id, u.google_id, u.email, u.name, u.profile_picture, u.created_at, u.updated_at
		  FROM sessions s JOIN users u ON u.id = s.user_id
		 WHERE s.token_hash = $1 AND s.expires_at > now()`, tokenHash))
}

func (s *Store) DeleteSession(ctx context.Context, tokenHash []byte) error {
	_, err := s.db.Exec(ctx, `DELETE FROM sessions WHERE token_hash = $1`, tokenHash)
	return err
}

func (s *Store) DeleteExpiredSessions(ctx context.Context) (int64, error) {
	tag, err := s.db.Exec(ctx, `DELETE FROM sessions WHERE expires_at <= now()`)
	return tag.RowsAffected(), err
}

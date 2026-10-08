// Root module used only by the Vercel Go runtime (api/index.go). The API
// itself lives in ./backend, its own module.
module github.com/vedant-workspaces/pacebook

go 1.24.7

require github.com/vedant-workspaces/pacebook/backend v0.0.0

require (
	cloud.google.com/go/compute/metadata v0.3.0 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/pgx/v5 v5.7.5 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	golang.org/x/crypto v0.37.0 // indirect
	golang.org/x/oauth2 v0.30.0 // indirect
	golang.org/x/sync v0.13.0 // indirect
	golang.org/x/text v0.24.0 // indirect
)

replace github.com/vedant-workspaces/pacebook/backend => ./backend

// Package migrations embeds the SQL migration files so the server binary can
// bring a fresh PostgreSQL database up to date on its own.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS

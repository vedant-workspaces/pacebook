# Loads .env if present so `make dev-api` picks up local settings.
ifneq (,$(wildcard .env))
include .env
export
endif

TEST_DATABASE_URL ?= postgres://pacebook:pacebook@localhost:5432/pacebook_test?sslmode=disable

.PHONY: db dev-api dev-web seed test test-api test-web build

db:            ## Start PostgreSQL in Docker
	docker compose up -d db

dev-api:       ## Run the Go API on :8080 (applies migrations on start)
	cd backend && go run ./cmd/server

dev-web:       ## Run the Vite dev server on :5173 (proxies /api to :8080)
	cd frontend && npm run dev

seed:          ## Seed sample data for EMAIL (default runner@pacebook.local)
	cd backend && go run ./cmd/seed -email $${EMAIL:-runner@pacebook.local}

test: test-api test-web

test-api:
	cd backend && go vet ./... && TEST_DATABASE_URL=$(TEST_DATABASE_URL) go test ./...

test-web:
	cd frontend && npm run typecheck && npm test

build:         ## Production build: backend/bin/pacebook + frontend/dist
	cd frontend && npm ci && npm run build
	cd backend && CGO_ENABLED=0 go build -o bin/pacebook ./cmd/server

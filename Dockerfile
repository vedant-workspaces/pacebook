# syntax=docker/dockerfile:1
# Single image: the Go API serves the built React app from /app/public.

FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# Contact email shown on /privacy and /terms (Render passes env vars as build args).
ARG VITE_CONTACT_EMAIL
ENV VITE_CONTACT_EMAIL=$VITE_CONTACT_EMAIL
# Google Search Console verification token (meta-tag method).
ARG VITE_GOOGLE_SITE_VERIFICATION
ENV VITE_GOOGLE_SITE_VERIFICATION=$VITE_GOOGLE_SITE_VERIFICATION
RUN npm run build

FROM golang:1.24-alpine AS api
WORKDIR /src
ENV GOTOOLCHAIN=local CGO_ENABLED=0
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ ./
RUN go build -trimpath -ldflags="-s -w" -o /out/pacebook ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app
COPY --from=api /out/pacebook /app/pacebook
COPY --from=web /web/dist /app/public
ENV APP_ENV=production STATIC_DIR=/app/public PORT=8080
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/app/pacebook"]

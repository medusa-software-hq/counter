# Build Frontend
FROM node:24-alpine AS frontend-builder
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .
RUN npx vite build

# Go Dependencies
FROM golang:1.26-alpine AS deps
WORKDIR /app

COPY backend/go.mod ./
RUN go mod download

# Compile Go Binary
FROM deps AS builder

COPY backend/ .
RUN go build -o main .

# Final Runtime
FROM alpine:latest
WORKDIR /app

COPY --from=builder /app/main .
COPY --from=frontend-builder /app/dist ./frontend/dist

ENV FRONTEND_DIST_DIR=frontend/dist

CMD ["./main"]

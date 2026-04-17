# --- Stage 1: Go Dependencies ---
FROM golang:1.23-alpine AS deps
WORKDIR /app
COPY backend/go.mod backend/go.sum ./
RUN go mod download

# --- Stage 2: Compile Go Binary ---
FROM deps AS builder
COPY backend/ .
RUN go build -o main .

# --- Stage 3: Final Runtime ---
FROM alpine:latest
WORKDIR /app

# Copy the binary — only rebuilds this layer if .go files changed.
COPY --from=builder /app/main .

# Copy static assets — only rebuilds these layers if HTML/JS changed.
COPY frontend/dist/index.html .
COPY frontend/dist/js/ ./js/

CMD ["./main"]

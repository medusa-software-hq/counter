# --- Stage 1: Go Dependencies ---
FROM golang:1.26-alpine AS deps
WORKDIR /app
COPY go.mod ./
RUN go mod download

# --- Stage 2: Compile Go Binary ---
FROM deps AS builder
COPY . .
RUN go build -o main .

# --- Stage 3: Final Runtime ---
FROM alpine:latest
WORKDIR /app

COPY --from=builder /app/main .

CMD ["./main"]

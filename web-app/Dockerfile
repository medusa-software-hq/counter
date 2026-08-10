# Build Frontend
FROM node:24-alpine AS frontend-builder

# Install buf CLI
RUN apk add --no-cache curl && \
    curl -sSL "https://github.com/bufbuild/buf/releases/download/v1.68.3/buf-Linux-x86_64" \
      -o /usr/local/bin/buf && \
    chmod +x /usr/local/bin/buf

WORKDIR /app

COPY frontend/package.json frontend/yarn.lock frontend/.yarnrc.yml ./
COPY frontend/.yarn ./.yarn
RUN node .yarn/releases/yarn-4.14.1.cjs install --immutable

COPY frontend/ .
COPY --from=proto . /proto/

RUN buf generate

ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_GOOGLE_HD
ARG VITE_API_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_GOOGLE_HD=$VITE_GOOGLE_HD
ENV VITE_API_URL=$VITE_API_URL

RUN npx vite build

# Build server
FROM golang:1.26-alpine AS server-builder
WORKDIR /src
COPY server/go.mod server/go.sum ./
RUN go mod download
COPY server/ ./
RUN CGO_ENABLED=0 go build -trimpath -o /server .

# Final runtime
FROM gcr.io/distroless/static-debian12
COPY --from=server-builder /server /server
COPY --from=frontend-builder /app/dist /srv
ENV STATIC_DIR=/srv
ENTRYPOINT ["/server"]

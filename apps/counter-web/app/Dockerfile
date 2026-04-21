# Build Frontend
FROM node:24-alpine AS frontend-builder

# Install buf CLI
RUN apk add --no-cache curl && \
    curl -sSL "https://github.com/bufbuild/buf/releases/download/v1.68.3/buf-Linux-x86_64" \
      -o /usr/local/bin/buf && \
    chmod +x /usr/local/bin/buf

WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .
COPY --from=proto . /proto/

RUN buf generate

ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_GOOGLE_HD
ARG VITE_COUNTER_SERVICE_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_GOOGLE_HD=$VITE_GOOGLE_HD
ENV VITE_COUNTER_SERVICE_URL=$VITE_COUNTER_SERVICE_URL

RUN npx vite build

# Final Runtime
FROM caddy:2-alpine

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend-builder /app/dist /srv

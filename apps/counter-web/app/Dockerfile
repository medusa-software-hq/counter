# Build Frontend
FROM node:24-alpine AS frontend-builder
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .
RUN npx vite build

# Final Runtime
FROM caddy:2-alpine

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend-builder /app/dist /srv

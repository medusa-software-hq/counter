# Build Frontend
FROM node:24-alpine AS frontend-builder
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .

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

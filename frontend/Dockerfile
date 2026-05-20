# syntax=docker/dockerfile:1.7
#
# Frontend image — Vite 5 + React 18 build, served by nginx 1.27.
# Stack mandated by .claude/rules/upgrade-policy.md §1 and project-info.md §4.2.
# pnpm via Corepack pins to the version declared in package.json#packageManager.

# ---------- Stage 1: build (Node 20 + pnpm) ----------
FROM node:20-alpine AS build

WORKDIR /workspace

# Lockfile + manifests first for layer caching.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# Corepack ships with Node; activate the pinned pnpm.
RUN corepack enable \
 && corepack prepare pnpm@11.1.2 --activate \
 && pnpm install --frozen-lockfile

# Copy the rest of the sources and produce dist/. `public/` is not part of the
# current scaffold; add a `COPY public ./public` line here when it lands.
COPY tsconfig*.json vite.config.ts eslint.config.js tailwind.config.ts postcss.config.js index.html ./
COPY src ./src

# Build-time public env. Default /api routes through the nginx proxy in stage 2,
# so the production bundle is environment-agnostic. Override via --build-arg if needed.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN pnpm build

# ---------- Stage 2: runtime (nginx serving static dist/) ----------
FROM nginx:1.27-alpine

# Non-root: nginx:alpine ships a `nginx` user; switch the master process to it via the
# upstream entrypoint (already handles permissions on /var/cache/nginx and /var/run).
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
  CMD wget -qO- http://localhost/healthz || exit 1

# Onboarding

This page is the runbook for bringing the DigitalWallet stack up locally from a fresh clone.

**Goal:** Get the DigitalWallet stack running locally in under 20 minutes once all prerequisites are installed.

## Prerequisites

| Tool | Min version | Source |
|---|---|---|
| Java JDK | 21 (LTS) | Eclipse Temurin / Azul Zulu / Liberica `(verify)` |
| Maven wrapper | bundled `mvnw` | shipped under `backend/mvnw` |
| Node.js | 20.x (Vitest + Playwright support) `(verify)` | nodejs.org / nvm |
| pnpm | latest | `npm install -g pnpm` |
| Docker Engine | 24.x or newer `(verify)` | docker.com |
| Docker Compose | v2 (`docker compose` plugin) | bundled with Docker Desktop, or `apt install docker-compose-plugin` |
| Git | 2.40+ `(verify)` | git-scm.com |

Source of truth for the stack and versions: [../../project-info.md §4](../../project-info.md#4-tech-stack-mandated).

## Step-by-step bring-up

All steps below are scaffolded against the mandated stack in [../../project-info.md §4](../../project-info.md#4-tech-stack-mandated) and the module layout in [../architecture/README.md](../architecture/README.md).

1. **Clone the repo and check out `main`.**
   ```bash
   git clone https://github.com/tienkhoa03/DigitalWallet.git
   cd DigitalWallet
   ```
2. **Copy the per-tier environment templates.**
   ```bash
   cp backend/env.template  backend/.env
   cp frontend/env.template frontend/.env
   ```
   Fill in the variables documented in each template. Backend secrets — `DB_PASSWORD`, `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY`, and (when the advisor path is exercised) `LLM_API_KEY` — MUST NOT have committed defaults ([../../.claude/rules/security.md §1](../../.claude/rules/security.md)). The frontend tier holds public-only config (anything prefixed with `VITE_` is readable in the browser bundle).
3. **Bring up the infrastructure stack** (Postgres 16, Kafka, Redis 7) from the backend compose. This also creates the shared `dw-net` network that the frontend compose joins later.
   ```bash
   docker compose -f backend/docker-compose.yml up -d
   ```
   For a production-like local run that also containerises the Quarkus backend, enable the `app` profile:
   ```bash
   docker compose -f backend/docker-compose.yml --profile app up -d --build
   ```
4. **Apply database migrations.** Flyway runs automatically on backend startup; for an explicit pre-migration run use the Flyway plugin.
   ```bash
   cd backend && ./mvnw flyway:migrate
   ```
   See [../database/migrations.md](../database/migrations.md).
5. **Start the backend in Quarkus dev mode** (skip if you already brought up the `--profile app` container in step 3).
   ```bash
   cd backend && ./mvnw quarkus:dev
   ```
   The dev UI is exposed on `http://localhost:8080/q/dev/` by Quarkus default `(verify once port is committed)`.
6. **Install frontend dependencies and start the dev server.** The Vite dev server proxies `/api/*` and WebSocket upgrades to the backend on `localhost:8080`, matching the production nginx config.
   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```
   For a production-like local run, bring up the frontend compose (requires the backend's `dw-net` to already exist — step 3):
   ```bash
   docker compose -f frontend/docker-compose.yml up -d --build
   # browse http://localhost:${FRONTEND_HOST_PORT:-8090}
   ```
7. **Run the test suites.**
   ```bash
   cd backend && ./mvnw test        # JUnit 5 + Testcontainers
   cd backend && ./mvnw verify      # adds JaCoCo coverage gate (≥80% service layer)
   pnpm --dir frontend test         # Vitest
   pnpm --dir frontend e2e          # Playwright smoke
   ```

## Something broken?

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend fails on startup with `Connection refused` to Postgres | The infra stack is not up, or `DB_URL` points at the wrong host. | `docker compose ps` to confirm `postgres` is `healthy`; check `DB_URL` resolves to the compose service name (e.g. `jdbc:postgresql://postgres:5432/wallet`) when running inside the compose network, or `localhost` when running outside it. |
| Kafka consumer logs `org.apache.kafka.common.errors.TimeoutException` on startup | Kafka broker is still electing controllers, or `KAFKA_BOOTSTRAP_SERVERS` is wrong. | Wait ~10 s after `docker compose up`; verify with `docker compose logs kafka` that the broker is `started`. Confirm the env var matches the advertised listener. |
| Redis lock helper rejects every transfer with "lock not acquired" | Redis is unreachable, or another process is holding the `wallet:<id>` lock from a prior crashed run. | `redis-cli -u $REDIS_URL ping` should return `PONG`. If a stale lock exists, inspect with `redis-cli keys 'wallet:*'` and only delete the specific stale key — not via `FLUSHALL`, which discards idempotency and rate-limit state. |
| Flyway migration fails with `Validate failed: Detected applied migration not resolved locally` | A migration file was renamed or removed after being applied to the local database. | Investigate the diverging history — never `flyway repair` blindly. See [../database/migrations.md](../database/migrations.md#forward-only-policy). |
| Frontend WebSocket connection drops or never opens | Backend WebSocket endpoint is not yet wired, or the dev server proxy is not forwarding `ws://`. | Confirm the endpoint is up (`curl -i -H 'Upgrade: websocket' ...`) and that the frontend dev proxy forwards both `http` and `ws` schemes. |
| LLM advisor returns "advice unavailable" immediately | The SmallRye circuit breaker is open after repeated upstream failures, or `LLM_API_KEY` is missing. | Inspect the circuit-breaker metrics; verify the env vars in [../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles); see [NFR8](../business-rules/README.md). |

## Related reading

- [../architecture/README.md](../architecture/README.md) — system shape and module layout.
- [../database/README.md](../database/README.md) — entity model.
- [../testing/README.md](../testing/README.md) — how the suites are structured and what the coverage gate enforces.

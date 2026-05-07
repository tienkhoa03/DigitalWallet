# Onboarding

Goal: get the Digital Wallet stack running locally in under 30 minutes.

> **Status:** the codebase is not yet scaffolded — only [../../README.md](../../README.md) (spec) and [../../CLAUDE.md](../../CLAUDE.md) exist. Steps below describe the **target** workflow once the backend module, frontend module, and `docker-compose.yml` land. Items marked **(spec — not yet implemented)** are derived from [../../README.md §2](../../README.md) and have not been verified against running code.

## Prerequisites

| Tool | Minimum version | Source |
|---|---|---|
| JDK | 21 | [../../README.md §2](../../README.md) |
| Maven | bundled as `./mvnw` (Quarkus Maven wrapper) once scaffolded | required by [docs/decisions/0001](../decisions/0001-quarkus-over-spring-boot.md) |
| Node.js | 20+ (verify — Angular 17 baseline) | inferred from Angular 17+ requirement |
| Docker Engine | 24+ (verify) | [../../README.md §2](../../README.md) |
| Docker Compose | v2 | [../../README.md §2](../../README.md) |

## Steps (spec — not yet implemented)

1. **Clone**
   ```bash
   git clone <remote>
   cd DigitalWallet
   ```
2. **Bring up infrastructure** — Postgres, Kafka, Redis, optional InfluxDB.
   ```bash
   docker compose up -d   # (spec — compose file not yet committed)
   ```
3. **Apply migrations** — `quarkus-flyway` runs at app boot when `quarkus.flyway.migrate-at-start=true`; the same migrations can be applied manually with `./mvnw flyway:migrate`.
4. **Run the backend** — `./mvnw quarkus:dev` (live reload + continuous testing). Default port: `8080`.
5. **Run the frontend** —
   ```bash
   cd frontend && npm install && npm start    # (verify)
   ```
   Default Angular dev port: `4200`.
6. **Smoke test** — open the admin dashboard in a browser; confirm the WebSocket alert stream connects (FR3.2).

## Something broken?

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend fails to start with "Connection refused" to Postgres | Compose stack not up | `docker compose ps`; then `docker compose up -d` |
| Migrations fail with `relation already exists` | Stale local DB volume | `docker compose down -v` to wipe volumes (destroys local data) |
| Kafka consumer not receiving events | Topic missing or wrong broker URL | Confirm `transaction-events` and `fraud-alerts` topics exist; check broker bootstrap config |
| Admin dashboard shows no live alerts | WebSocket endpoint unreachable | Confirm backend WebSocket path matches frontend client config |
| `Idempotency-Key already processed` returned unexpectedly | Redis still holds the key from a prior run | Flush the Redis instance or wait for the key TTL to expire |

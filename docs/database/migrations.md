# Migrations

Flyway-managed, forward-only. Migration files live under (path verify, conventionally `backend/src/main/resources/db/migration/V<n>__<slug>.sql`).

## Log

| # | File | Purpose |
|---|---|---|
| — | — | _No migrations committed yet — see [../../README.md §3](../../README.md) for the planned schema and [README.md](README.md) for the spec-derived ERD._ |

## Writing a new migration

1. **Name** it `V<next>__<short_snake_case>.sql` — never reuse a number, never edit a committed migration.
2. **One logical change per file** (one table, one column, one index, one constraint).
3. **Forward-only** — to undo, write a new compensating migration.
4. **Pair the entity with the migration in the same PR** — JPA entity, repository, and migration land together.
5. **Seed data** belongs in a separate file: `R__seed_<topic>.sql` (repeatable) or `V<n>__seed_<topic>.sql` (versioned), not mixed with schema DDL.

## Testing locally

```bash
docker compose down -v          # wipes the Postgres volume — destroys local data
docker compose up -d postgres
# then run the project's migrate target (verify build tool & module path)
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Validate failed: migrations have failed validation` | A committed migration was edited after running once locally. | Wipe the local DB volume; never edit a migration after merge. |
| `relation "x" already exists` | Manual schema changes drifted from migrations. | Wipe and re-run; treat the dev DB as disposable. |
| `Found non-empty schema(s) without baseline` | Existing schema not under Flyway control. | Run `flyway:baseline` once and document the baseline version. |

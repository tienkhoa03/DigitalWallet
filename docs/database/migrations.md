# Database migrations

This page documents how schema changes are introduced to the DigitalWallet ledger.

## Tool

**Flyway**, versioned SQL only ([../../project-info.md §4.1](../../project-info.md#41-backend)). No runtime schema mutation. Hibernate's `hbm2ddl.auto` is `none` in every profile `(verify when persistence config lands)`. Flyway runs automatically at backend startup; an explicit run is available via the Flyway Maven plugin (see [../onboarding/README.md](../onboarding/README.md)).

## Naming convention

Flyway versioned migrations:

```
V<version>__<short_snake_case_description>.sql
```

- `<version>` is a monotonically increasing integer (`V1`, `V2`, `V3`, …) or dotted form (`V1.1`, `V1.2`). Use plain integers unless a hotfix needs to land between two existing versions `(verify)`.
- `<short_snake_case_description>` summarises the change in present-tense, e.g. `V14__add_idempotency_key_table.sql`.
- Use `R__<description>.sql` only for genuinely repeatable objects (views, functions) — never for tables.
- Bundle related DDL changes in a single migration; do not split one logical change across multiple files.

## Forward-only policy

Migrations are **forward-only**.

- A migration that has been applied to any shared environment (CI, staging, prod) **must not be modified or deleted**. Fix forward with a new migration.
- Local-only iteration: developers may freely reset their personal Postgres container (`docker compose down -v`) and re-apply the full migration history.
- `flyway repair` is reserved for legitimate checksum recovery (e.g. a migration's whitespace changed). It must not be used to paper over a renamed or removed migration on a shared environment — investigate first.
- Reversible-by-default does not apply: there are no Flyway `undo` migrations in MVP `(verify)`. To reverse a change, write a follow-up forward migration.

## Seed data

- **Reference seeds** (currencies, FX rates, default categories) are applied via Flyway versioned migrations so every environment converges to the same baseline.
- **Demo / fixture data** for local development belongs under `backend/postgres/init/` (SQL fragments invoked by the Postgres container's `docker-entrypoint-initdb.d` hook from `backend/docker-compose.yml`), **not** under Flyway. This keeps test fixtures out of the production migration timeline.
- The `fx_rates` table is populated by a Flyway seed migration and is otherwise mutated only through an admin-only path ([../../project-info.md §9](../../project-info.md#9-domain-glossary), [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)).
- Test suites that need bespoke data must insert it within the test (or via Testcontainers init scripts) — they must never depend on demo-fixture rows.

## Conventions for the migration content

- All identifiers `snake_case`; PKs are `id uuid`; timestamps `timestamptz` ([../README.md](README.md#naming-conventions)).
- Enum-like columns use `varchar` with `CHECK` constraints rather than Postgres `ENUM` types, so values can evolve in plain SQL.
- Every foreign-key column ships with an explicit index in the same migration `(verify)`.
- Money columns are `numeric(19,4)` ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)).
- A migration that drops a column must first be deployed without read traffic on that column; coordinate with code in a preceding release `(verify pattern once code lands)`.

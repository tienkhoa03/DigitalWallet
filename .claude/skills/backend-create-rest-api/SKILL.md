---
name: backend-create-rest-api
description: Scaffold a complete backend vertical slice for a new REST resource — Flyway migration, JPA entity, Panache repository, request/response DTOs, service interface and implementation, JAX-RS resource, and unit test — wired through the feature-module layout. Invoke when the user asks to "add a new endpoint", "create a REST resource for X", "scaffold a new API for X", "wire up a CRUD endpoint", "add a new feature module", "expose a new resource", "scaffold the wallet/account/budget/... API", or "give me the controller + service + repo for X".
---

# Backend — Create REST API (vertical slice)

This skill scaffolds the full backend vertical slice for a new REST resource inside an existing feature module under `backend/`. It cites the coding contract instead of restating it; every conventional choice points at a rule section.

## When NOT to invoke

- The user only wants to add a single method to an existing resource — edit the file directly.
- The user wants to add a Kafka consumer with no HTTP surface — that is a `consumer/` job under the feature module, governed by [backend_coding.md §15](../../rules/backend_coding.md).
- The user wants to scaffold a Flyway migration alone — write the SQL file directly per [backend_coding.md §13](../../rules/backend_coding.md).
- The user wants to scaffold frontend code — use `frontend-implement-ui-component` instead.

## Step 1 — Gather inputs

Read [`CLAUDE.md`](../../../CLAUDE.md), [`project-info.md §3`](../../../project-info.md#3-architecture-style), and [`docs/api/README.md`](../../../docs/api/README.md) to confirm the feature-module list and the canonical endpoint catalog. Inspect `backend/` to confirm the target module is scaffolded. If `backend/` (or the target feature module) does not yet exist on disk, **stop** and tell the user — do NOT bootstrap build files, `pom.xml`, or module skeletons from this skill.

Then collect missing inputs in a single `AskUserQuestion` call (ask only what the user did not already provide):

1. **Resource name** — singular CamelCase (e.g. `Budget`, `Wallet`, `Transfer`).
2. **Operations** — multi-select from `Create / Read / List / Update / Delete`.
3. **Auth policy per operation** — which role(s) per operation, from [project-info.md §2.2](../../../project-info.md#22-roles-in-the-system) (`USER`, `ADMIN`, `FRAUD_ANALYST`) — server-side RBAC is mandatory per [security.md §3](../../rules/security.md#3-authorization).
4. **Constraints** — `Idempotency-Key required` (mutating money endpoints per [backend_coding.md §2](../../rules/backend_coding.md) and NFR3), `Hybrid concurrency + fraud pre-check` (wallet mutations: synchronous fraud pre-check before the Redis lock per [backend_coding.md §3](../../rules/backend_coding.md) and NFR9, then Redis lock + DB `PESSIMISTIC_WRITE` per NFR1), or `Neither`.

Also gather (free-form, in the same turn or follow-up): the **feature module** to land in (`account`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`, `shared`), the **base path** (must match [docs/api/README.md](../../../docs/api/README.md)), and the **entity fields** (name, Java type, nullability, validation hints — money is `BigDecimal`, timestamps are `Instant`, currency is `varchar(3)` per [backend_coding.md §4](../../rules/backend_coding.md)).

## Step 2 — Confirm placement

Print the planned file list with the chosen feature module, then halt for the user to confirm:

```
backend/<module>/
├── api/
│   ├── <Resource>Resource.java
│   ├── <Action><Resource>Request.java     (per operation)
│   └── <Resource>Response.java
├── service/
│   ├── <Resource>Service.java              (interface, only if cross-module or multi-impl per backend_coding.md §3)
│   └── <Resource>ServiceImpl.java          (or single class if interface skipped)
├── persistence/
│   ├── <Resource>Entity.java
│   └── <Resource>Repository.java
└── (event/ — only if the operation appends to the outbox)

backend/shared/db/migration/
└── V<next>__create_<resource_snake>.sql

backend/src/test/java/<module>/service/
└── <Resource>ServiceImplTest.java
```

Confirm:
- The target module exists.
- The base path matches [docs/api/README.md](../../../docs/api/README.md).
- The next Flyway version slot is free per [backend_coding.md §13](../../rules/backend_coding.md).
- Cross-module imports do not violate [backend_coding.md §1](../../rules/backend_coding.md). (E.g. `pfm/` MUST NOT add a repository on ledger tables.)

## Step 3 — Write files (bottom-up)

Generate the files in dependency order so each one compiles against the previous:

1. **Flyway migration** at `backend/shared/db/migration/V<next>__create_<resource_snake>.sql` — versioned SQL only, snake_case identifiers, money as `numeric(19,4)`, timestamps as `timestamptz`, ISO 4217 currency as `varchar(3)`. Per [backend_coding.md §13](../../rules/backend_coding.md) and [backend_coding.md §4](../../rules/backend_coding.md).
2. **Entity** at `<module>/persistence/<Resource>Entity.java` — UUID PK, `@ManyToOne(fetch = LAZY)` defaults, `BigDecimal` for money, `Instant`/`OffsetDateTime` for timestamps, `jakarta.*` namespace only. Per [backend_coding.md §4](../../rules/backend_coding.md) and [upgrade-policy.md §3](../../rules/upgrade-policy.md).
3. **Repository** at `<module>/persistence/<Resource>Repository.java` — `PanacheRepositoryBase<T, UUID>`, returns `Optional<T>` / `List<T>`, named-parameter queries only. Per [backend_coding.md §5](../../rules/backend_coding.md).
4. **Request / Response DTOs** at `<module>/api/` — Java `record`s; separate `Create…Request` vs `Patch…Request` types; never expose the entity. Per [backend_coding.md §6](../../rules/backend_coding.md) and [backend_coding.md §12](../../rules/backend_coding.md).
5. **Service** at `<module>/service/` — `@Transactional` on the method; constructor injection only; service-layer RBAC re-check; for wallet mutations follow the full money-path sequence: synchronous fraud pre-check (velocity FR2.1 + volume FR2.2 Redis counters + `account.fraud_status` read FR2.4) BEFORE the Redis wallet lock, then Redis lock → DB `PESSIMISTIC_WRITE` → outbox-write → commit → release Redis lock in `finally`. A pre-check breach writes one `audit_log` row + one `transaction.blocked` outbox event and rejects with `fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended`. Per [backend_coding.md §3](../../rules/backend_coding.md) (NFR1 + NFR9), [backend_coding.md §8](../../rules/backend_coding.md), and [security.md §3](../../rules/security.md).
6. **Resource** at `<module>/api/<Resource>Resource.java` — `@Path` references a path constant; `@RolesAllowed` on each method; `@Valid` on every body; `Idempotency-Key` header bound for mutating money endpoints; HTTP 202 + WebSocket reply for async request-reply endpoints. Per [backend_coding.md §2](../../rules/backend_coding.md) and [security.md §4](../../rules/security.md).
7. **Unit test** at the mirrored test package — JUnit 5 + Mockito; covers happy path, every domain exception, boundaries, replay, and the lock sequence where applicable. Per [backend_coding.md §14](../../rules/backend_coding.md) and [testing.md §2](../../rules/testing.md). Delegate the generation details to `backend-create-unit-test`.

Apply these cross-cutting rules without restating them:

- Sort parameters go through an explicit whitelist — [backend_coding.md §10](../../rules/backend_coding.md).
- Pagination defaults `page=0`, `pageSize=20`, capped at 100 — [backend_coding.md §10](../../rules/backend_coding.md).
- Mutating money endpoints with new rate-limit needs go through the shared Redis token bucket — [security.md §8](../../rules/security.md).
- Domain exceptions extend `shared.DomainException` and the global mapper handles the envelope — [backend_coding.md §8](../../rules/backend_coding.md).
- Admin reads of user data and mutating actions write an `audit_log` row — [security.md §3](../../rules/security.md) and [security.md §7](../../rules/security.md).
- New endpoints update the catalog in [docs/api/README.md](../../../docs/api/README.md) in the same PR — [backend_coding.md §2](../../rules/backend_coding.md).

## Step 4 — Self-check

Walk this list against the generated diff before reporting:

- [ ] No JPA entity exposed via JAX-RS — [backend_coding.md §6](../../rules/backend_coding.md).
- [ ] Constructor injection only — [backend_coding.md §3](../../rules/backend_coding.md).
- [ ] Service-layer `@RolesAllowed`-equivalent check present — [security.md §3](../../rules/security.md).
- [ ] Ownership check on every owner-scoped path parameter — [security.md §3](../../rules/security.md).
- [ ] `@Valid` on every body — [backend_coding.md §12](../../rules/backend_coding.md).
- [ ] Sort/filter parameters whitelisted — [backend_coding.md §10](../../rules/backend_coding.md).
- [ ] Native or JPQL queries use bound parameters — [security.md §4](../../rules/security.md).
- [ ] Money is `BigDecimal`; timestamps are `Instant`/`OffsetDateTime` — [backend_coding.md §4](../../rules/backend_coding.md).
- [ ] Domain exceptions extend `DomainException` and surface a typed `errorKey` — [backend_coding.md §8](../../rules/backend_coding.md).
- [ ] No Kafka publishing on the request thread — [backend_coding.md §15](../../rules/backend_coding.md), NFR2/NFR5.
- [ ] Mutating money endpoints require `Idempotency-Key` — [backend_coding.md §2](../../rules/backend_coding.md), NFR3.
- [ ] Wallet mutations run the synchronous fraud pre-check (velocity + volume + `account.fraud_status`) BEFORE the Redis lock, then follow the Redis-lock → DB-row-lock → outbox-write sequence; pre-check breaches surface `fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended` and write a `transaction.blocked` outbox event + `audit_log` row — [backend_coding.md §3](../../rules/backend_coding.md), [backend_coding.md §8](../../rules/backend_coding.md), NFR1 + NFR9.
- [ ] No secret material, no PII, no full `Idempotency-Key` in logs — [backend_coding.md §11](../../rules/backend_coding.md), [security.md §1](../../rules/security.md), [security.md §7](../../rules/security.md).
- [ ] Migration shipped alongside any entity change — [backend_coding.md §13](../../rules/backend_coding.md).

## Step 5 — Report

Report:

- The list of files written.
- Every place the user's request was adjusted to fit a rule, citing the rule (e.g. "added `Idempotency-Key` header binding per [backend_coding.md §2](../../rules/backend_coding.md)").
- Any rule that flagged a conflict with the request (e.g. "wallet mutation requires the Redis lock helper from `shared/`, which the current diff does not yet have — see [backend_coding.md §3](../../rules/backend_coding.md)").
- Suggested next step: invoke `backend-verify` to compile, run tests, and check the JaCoCo floor.

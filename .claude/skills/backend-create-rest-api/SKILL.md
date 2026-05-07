---
name: backend-create-rest-api
description: Use when the user asks to "create a REST API", "add an endpoint", "add a controller", "scaffold a new resource", "add a CRUD endpoint", "implement the backend for X", or any equivalent. Scaffolds a new Quarkus JAX-RS resource end-to-end (Flyway migration, JPA entity, Panache repository, DTOs, service, RESTEasy Classic resource, tests) following the layer order and rules in .claude/rules/backend_coding.md.
---

# Backend — Create REST API

Scaffold a complete vertical slice for a new resource on Quarkus. The slice spans Flyway migration → JPA entity → Panache repository → DTOs → service interface + impl → RESTEasy Classic JAX-RS resource → tests. Follow the existing project rules; do not invent conventions.

## When NOT to invoke

- Modifying an existing endpoint — read the file and edit, no scaffolding.
- Adding a Kafka consumer or producer — different layer order; do it manually with [.claude/rules/backend_coding.md](../../rules/backend_coding.md) §3 + the fraud/event module conventions.
- Frontend-only work — use `frontend-implement-ui-component`.

## Step 1 — Gather inputs

One `AskUserQuestion` call. Ask only what isn't already in the user's prompt:

| Question | Header |
|---|---|
| Resource name in singular CamelCase (e.g., `Wallet`)? | Resource |
| Which operations? (multi-select: Create, Read-by-id, List, Update PUT, Update PATCH, Delete) | Operations |
| Auth policy per operation, or "skip — auth scheme not yet committed"? | Auth |
| Constraints (multi-select: Idempotency-Key required, Pessimistic lock on read-modify-write, Neither) | Constraints |

Then ask the user for entity fields as free-text: `name : Java type : nullable? : JSON key`.

## Step 2 — Confirm placement

Before writing, print the planned file list and the chosen feature module (`wallet`, `transaction`, `fraud`, `admin`, or a new module). Stop and confirm.

Expected layout (substitute `<Resource>` / `<resource>` / `<feature>`):

```
backend/src/main/resources/db/migration/V<n>__create_<resource>s.sql
backend/src/main/java/.../<feature>/persistence/<Resource>.java
backend/src/main/java/.../<feature>/persistence/<Resource>Repository.java
backend/src/main/java/.../<feature>/api/dto/<Resource>Request.java   # if Create/Update
backend/src/main/java/.../<feature>/api/dto/<Resource>Response.java
backend/src/main/java/.../<feature>/service/<Resource>Service.java
backend/src/main/java/.../<feature>/service/<Resource>ServiceImpl.java
backend/src/main/java/.../<feature>/api/<Resource>Resource.java
backend/src/test/java/.../<feature>/service/<Resource>ServiceImplTest.java
```

If the backend module isn't scaffolded yet, stop and tell the user — this skill does not bootstrap `pom.xml` or the runtime.

## Step 3 — Write files bottom-up

Order matters: migration → entity → repository → DTOs → service interface → service impl → resource → test. Each lower layer must compile before the next is written.

| File | Rule to apply |
|---|---|
| Migration | [backend_coding.md §13](../../rules/backend_coding.md), [docs/database/migrations.md](../../../docs/database/migrations.md). Forward-only `V<n>__…` under `src/main/resources/db/migration/`; one logical change; `quarkus-flyway` runs it on startup. |
| Entity | [backend_coding.md §4](../../rules/backend_coding.md). UUID PK, `BigDecimal` money + `numeric(19,4)`, `OffsetDateTime`, explicit `FetchType.LAZY`, explicit `@JoinColumn`. POJO — Panache Active Record forbidden. |
| Repository | [backend_coding.md §5](../../rules/backend_coding.md). `PanacheRepositoryBase<Entity, ID>`. `Optional<T>` returns (`findByIdOptional`), `LockModeType.PESSIMISTIC_WRITE` on balance reads, parameter binding only. |
| DTOs | [backend_coding.md §6](../../rules/backend_coding.md). Records when immutable. Never expose entities. |
| Service | [backend_coding.md §3](../../rules/backend_coding.md). `@ApplicationScoped` + `@Transactional(REQUIRED)` on the impl. Inject a `Clock`. |
| Resource | [backend_coding.md §2](../../rules/backend_coding.md). `@Path` as `public static final` constant; DTO body only; `@RolesAllowed` or explicit `@PermitAll`. |
| Test | [backend_coding.md §14](../../rules/backend_coding.md), [testing.md §2](../../rules/testing.md). Spawn `backend-create-unit-test` for the service test. |

For idempotency, locking, and commit-then-publish ordering, copy the patterns from [docs/business-rules/transfer-rules.md](../../../docs/business-rules/transfer-rules.md) — do not paraphrase.

## Step 4 — Self-check

Walk this checklist; re-open files and fix any failure before reporting done.

- [ ] Migration file number is the next free `V<n>__…` and is forward-only (no edits to a committed migration).
- [ ] Entity uses `BigDecimal` + `numeric(19,4)` for money, `OffsetDateTime` + `timestamptz` for timestamps.
- [ ] All `@ManyToOne` / `@OneToOne` are `FetchType.LAZY` explicitly.
- [ ] No JPA entity is returned from any resource method.
- [ ] Sort and pagination (if applicable) use the whitelist + cap pattern from [backend_coding.md §10](../../rules/backend_coding.md).
- [ ] Every required DTO field has a Bean Validation annotation.
- [ ] Service implementation is `@ApplicationScoped` + `@Transactional`; transactions are NOT opened in the resource or repository.
- [ ] Two-wallet locks (if any) are acquired in ascending ID order — see [transfer-rules.md](../../../docs/business-rules/transfer-rules.md#lock-acquisition-order).
- [ ] State-changing transfer-style endpoints require `Idempotency-Key` header.
- [ ] Every resource method has `@RolesAllowed` or an explicit `@PermitAll`. Default-allow is forbidden ([security.md §3.1](../../rules/security.md)).
- [ ] No string concatenation into JPQL or native SQL.
- [ ] Test file follows `<method>_<scenario>_<expected>` naming and the mock decision matrix from [testing.md §2.2](../../rules/testing.md).

## Step 5 — Final report

Print:

- Files created (full paths, one-line purpose each).
- Rules where the request was adjusted to match policy (e.g., "user asked for arbitrary sortable fields → restricted to `created_at`, `amount` per backend §10.2").
- Open follow-ups (e.g., `// TODO(security): wire @RolesAllowed once auth ADR lands` if the auth scheme is still unchosen — see [docs/architecture/README.md §6](../../../docs/architecture/README.md)).
- Suggest invoking `backend-verify` next.

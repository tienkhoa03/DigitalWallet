# Implementation Plan: Phase 1 — Signup + Login (FR1.1 user identity)

- **Date:** 2026-05-25
- **Ticket:** n/a (derived from [implementation-plan-mvp-master.md §9 Epic 1 backend](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14))
- **Story points:** M — first vertical slice. Brings three cross-cutting `shared/*` packages, the first feature module (`user/`), the first Flyway migration, the first JaCoCo-gated service, and flips ADR 0001.
- **Milestone:** MVP Epic 1 — Phase 1 (`POST /users`, `POST /auth/login`)
- **Assignees:** unassigned
- **Affected modules (backend):** `shared/exception`, `shared/security`, `shared/validation`, `user/` (api, service, persistence). No `wallet/`, `fraud/`, `pfm/`, `advisor/`, `dashboard/` code.
- **Affected modules (frontend):** none (returns in Phase F2).
- **Affected docs / ADRs:** [docs/decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) flips **Proposed → Accepted**; [CLAUDE.md](../../CLAUDE.md) "Project Status" updated to reflect that Phase 1 ships; this master plan's Phase 1 row gets its "Plan" checkbox ticked.
- **Suggested branch name:** `feat/phase-1-signup-login`

---

## 2. Context / Problem Statement

After Phase 0 lands [implementation-plan-phase-0-layout-reconcile.md](implementation-plan-phase-0-layout-reconcile.md), the repository has `backend/` at the right path, the LTS Quarkus version pinned, the JaCoCo gate scoped to `com/digitalwallet/*/service/**`, and the JWT / Hibernate-Validator / Testcontainers dependencies on the classpath — but no business code, no Flyway migrations, and `quarkus.smallrye-jwt.enabled=false`. The system has no identity yet; no user can sign up, log in, or receive a JWT.

Phase 1 is the first vertical slice. Per [implementation-plan-mvp-master.md §9 Epic 1 backend](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14), this phase delivers FR1.1's identity half — signup + login — and the three cross-cutting `shared/*` packages that every later phase reuses:

- `shared/exception/` — the `DomainException` hierarchy, the canonical error envelope from [../api/README.md §Error response shape](../api/README.md#error-response-shape), and the single JAX-RS `ExceptionMapper` mandated by [../../.claude/rules/backend_coding.md §8](../../.claude/rules/backend_coding.md#8-exception-handling). Per-resource `try`/`catch` blocks that build JSON manually are a defect; the mapper has to land before any endpoint does.
- `shared/security/` — Argon2id password hashing ([../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication)), ES256 JWT issuance + verification, and the `UserRole` enum. Per the master plan, this phase ships **REST-only** JWT verification; the WebSocket-upgrade JWT validator is deferred (MVP has no WS endpoint).
- `shared/validation/` — `@CurrencyCode` Bean Validation annotation for the immutable `user.base_currency` field and (in Phase 2) the `wallet.currency` field, per [../business-rules/core-wallet-management-rules.md FR1.1](../business-rules/core-wallet-management-rules.md#fr11--user-signup-and-wallet-opening).

The slice also brings:

- **Flyway V1** — the `user` table with the exact column set committed in [../database/README.md `user`](../database/README.md): `id uuid PK`, `email varchar UNIQUE NOT NULL`, `password_hash varchar NOT NULL`, `role varchar NOT NULL CHECK (role IN ('USER','ADMIN')) DEFAULT 'USER'`, `base_currency char(3) NOT NULL`, `fraud_status varchar NOT NULL CHECK (fraud_status IN ('ACTIVE','SUSPENDED')) DEFAULT 'ACTIVE'`, `created_at timestamptz NOT NULL`. The `fraud_status` column ships now (master plan §2: "column ships now for post-MVP fraud; not read by any MVP code") so the post-MVP fraud module can wire without a destructive migration. The table is quoted as `"user"` per the Postgres-quoting note in [../database/README.md](../database/README.md).
- **`POST /users`** and **`POST /auth/login`** as defined in [../api/README.md Epic 1](../api/README.md). Signup returns `{ user_id, created_at }` with HTTP 201; login returns `{ access_token, token_type, expires_in }` with HTTP 200.
- **Enumeration-resistant login** — sign-in failures MUST return the same `error_key: "auth.invalid_credentials"` regardless of whether the email exists, with a constant-time path that hashes against a sentinel when the user is missing ([../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication)).
- **ADR 0001 flipped to Accepted** — ES256, with the operational details captured in Open Q #10 of the master plan: dev keypair shipped at `src/main/resources/META-INF/jwt-public-key-dev.pem`, referenced via `mp.jwt.verify.publickey.location`; private key resolved from the `JWT_PRIVATE_KEY` env var (PEM, with literal `\n` escapes) at runtime by the `JwtIssuer` startup bean.

**Desired end state.** After this PR merges:

- A user can `POST /users` with `{ email, password, base_currency }` and receive `201` + `{ user_id, created_at }`; the password is Argon2id-hashed; `base_currency` is rejected if not ISO 4217; duplicate emails are rejected with `409 user.email_taken`.
- A user can `POST /auth/login` and receive a signed ES256 JWT carrying `sub = user_id`, `groups = ["USER"]` (Quarkus SmallRye JWT convention for roles), `iss = digitalwallet`, `aud = digitalwallet-api`, `exp = iat + 3600`.
- The JWT verifier is wired and **enabled** (`quarkus.smallrye-jwt.enabled=true`); a hand-crafted protected probe in tests confirms `@RolesAllowed("USER")` accepts a token signed by `JwtIssuer` and rejects an unsigned token, `alg: none`, an HS256 token, and an expired token (>30s skew).
- The `DomainException` hierarchy + mapper produce the canonical envelope from [../api/README.md §Error response shape](../api/README.md#error-response-shape) for every failure path; Hibernate Validator failures map to `validation.invalid_payload` with a `details` array per [../../.claude/rules/backend_coding.md §8](../../.claude/rules/backend_coding.md#8-exception-handling).
- `./mvnw -B verify` is green; JaCoCo on `com/digitalwallet/*/service/**` is ≥ 80 % (NFR4).
- [docs/decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) reads **Accepted**.

This phase does **not** open the wallet path, the money path, the outbox, the Redis lock, the rate-limit middleware, or any consumer.

## 3. Scope

### In Scope

- `shared/exception/` — `DomainException` sealed hierarchy, error envelope record, JAX-RS `ExceptionMapper<DomainException>`, `ExceptionMapper<ConstraintViolationException>` for Hibernate Validator failures.
- `shared/security/` — `Argon2Hasher` (Bouncy Castle Argon2BytesGenerator), `JwtIssuer` (ES256 sign), `UserRole` enum, `JwtSigningKeyProvider` that parses `JWT_PRIVATE_KEY` PEM at startup. JWT **verification** is provided by `quarkus-smallrye-jwt` (the existing dependency) — we do not roll our own verifier.
- `shared/validation/` — `@CurrencyCode` annotation + `CurrencyCodeValidator` (checks against `java.util.Currency.getAvailableCurrencies()`; rejects empty / wrong-length / unknown codes).
- `user/persistence/` — `UserEntity` JPA entity mapped to `"user"`, `UserRepository extends PanacheRepositoryBase<UserEntity, UUID>` with `findByEmail(String)`.
- `user/service/` — `UserService.signup(CreateUserRequest)` (validate currency, hash password, enforce email uniqueness, persist), `AuthService.login(LoginRequest)` (constant-time verify + token issue).
- `user/api/` — `UserResource` exposing `POST /users`; `AuthResource` exposing `POST /auth/login`. Both DTO sets as Java `record`s. Path constants per [../../.claude/rules/backend_coding.md §2](../../.claude/rules/backend_coding.md#2-routing--controllers).
- Flyway V1 migration `V1__create_user_table.sql` under `src/main/resources/db/migration/`. Adds `quarkus-flyway` to `pom.xml`.
- `application.properties` updates — enable `smallrye-jwt`, configure `mp.jwt.verify.publickey.location`, `mp.jwt.verify.issuer`, `mp.jwt.verify.audiences`, expose `app.jwt.*` for the issuer side, enable Flyway migrations on startup.
- `src/main/resources/META-INF/jwt-public-key-dev.pem` — dev public key, committed (verifier key is non-secret per [../../.claude/rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration)).
- `src/test/resources/META-INF/jwt-public-key-test.pem` + matching private key — fixed keypair used only by integration tests.
- `src/test/resources/application.properties` — test profile overrides (Testcontainers JDBC URL hook, test JWT key locations).
- Unit tests for `UserService`, `AuthService`, `Argon2Hasher`, `JwtIssuer`, `CurrencyCodeValidator`, `DomainExceptionMapper`.
- Integration tests for `POST /users` and `POST /auth/login` via `@QuarkusTest` + `@QuarkusTestResource(PostgresTestResource.class)` (Testcontainers Postgres 16) + RestAssured.
- `docs/decisions/0001-jwt-signing-algorithm.md` flipped to Accepted with decision text capturing Open Q #10's resolution.
- `CLAUDE.md` "Project Status" updated to mark Phase 1 complete and to note that the JWT verifier is now enabled.
- `implementation-plan-mvp-master.md` Phase 1 "Plan" checkbox ticked when this plan is approved.

### Out of Scope

- Any `wallet/` code (Phase 2).
- Any `Idempotency-Key` middleware (Phase 3). Signup + login are not money-mutation endpoints and per [../../.claude/rules/backend_coding.md §2](../../.claude/rules/backend_coding.md#2-routing--controllers) the header is only required on deposit / withdraw / transfer.
- Any Redis lock helper / Redis dependency at runtime (Phase 3). Redis URL stays defaulted in `application.properties` from Phase 0; no runtime client wired.
- Any rate-limit middleware (Phase 6). `POST /auth/login` progressive back-off is `<!-- not-yet-adopted -->` per [../../.claude/rules/security.md §8](../../.claude/rules/security.md#8-rate-limiting--abuse) and stays deferred in MVP.
- Any WebSocket endpoint or WS-upgrade JWT validator (deferred MVP-wide).
- Any CORS allow-list — first non-test consumer is the frontend in Phase F1 / F2.
- Any security headers (HSTS, CSP, X-Frame-Options) — Phase F1 owns the first browser surface; MVP backend ships headers as the first frontend phase lands. *(Master plan §11 lists "security headers shipped in Phase 1"; this plan defers them to F1 to avoid wiring Quarkus filters with no consumer to validate them. The deferral is captured as Open Q #6.)*
- LLM, advisor, fraud, PFM, dashboard, observability — out of phase per master plan §2.
- `audit_log` — deferred MVP-wide per [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md).
- Password-reset / account-recovery flow — deferred (MVP master plan does not list a `POST /auth/reset` row); [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) requires it to be a separate authenticated flow when it ships.
- "Remember me" UX — frontend concern (Phase F2).

## 4. Open Questions (MANDATORY)

Status legend: **Unanswered (open)** | **Answered (closed)** | **Deferred (tracked elsewhere)**

| # | Question | Status | Resolution / Owner |
|---|---|---|---|
| 1 | Argon2id library? | Answered | **Bouncy Castle** `org.bouncycastle:bcprov-jdk18on` — pure-Java `Argon2BytesGenerator`. Parameters: `type = Argon2id`, `memoryAsKB = 65536` (64 MiB), `iterations = 3`, `parallelism = 1`, `salt length = 16 bytes`, `hash length = 32 bytes`. Matches the OWASP 2024 minimums for Argon2id and stays inside the [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) "Argon2id preferred" rule. No native binary in the container image. (User direction 2026-05-25.) |
| 2 | JWT access-token TTL? | Answered | **3600 s (1 hour).** Returned to clients via `expires_in` in the `POST /auth/login` response. No refresh token in MVP; the user re-logs in. (User direction 2026-05-25.) |
| 3 | ES256 public-key shipment mechanism? | Answered | **Classpath PEM** at `src/main/resources/META-INF/jwt-public-key-dev.pem`, referenced via `mp.jwt.verify.publickey.location=META-INF/jwt-public-key-dev.pem`. Overridable per profile via `MP_JWT_VERIFY_PUBLICKEY_LOCATION` env var when prod ships. The dev key is non-secret per [../../.claude/rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration); private key remains env-var only. (User direction 2026-05-25.) |
| 4 | JWT claim shape — `iss` / `aud` / `groups`? | Answered | `iss = digitalwallet`, `aud = digitalwallet-api`, `groups = [ <role string> ]` (Quarkus SmallRye JWT convention — `mp.jwt.verify.issuer` + `mp.jwt.verify.audiences` enforced; `@RolesAllowed("USER")` matches the `groups` claim). `sub = user.id` as a UUID string. `iat` set to `Instant.now()`; `exp = iat + 3600`. |
| 5 | Where does the JWT issuer read its private key from at runtime? | Answered | Env var `JWT_PRIVATE_KEY` (PEM, single-line — `BEGIN`/`END` headers preserved, internal newlines escaped as literal `\n`). A `@Startup` `@ApplicationScoped` `JwtSigningKeyProvider` parses the value once into an `ECPrivateKey` via `KeyFactory.getInstance("EC")` + `PKCS8EncodedKeySpec`. `JwtIssuer` injects the provider, calls `Jwt.claims(...).sign(privateKey)` from `io.smallrye.jwt.build.Jwt`. If the env var is missing in `prod`, application startup fails fast with a clear log line. |
| 6 | Security headers (HSTS, CSP, X-Frame-Options, …) in Phase 1? | Deferred | Deferred to Phase F1 (frontend bootstrap). The master plan §11 originally placed them in Phase 1, but adding them now with no browser consumer makes them untestable. Phase F1 owns the wiring (`quarkus-vertx-http` filter) + the matching CSP allow-list against the frontend asset origins. |
| 7 | Email format validation — `jakarta.validation.constraints.Email` strict, or a custom RFC 5322 regex? | Answered | `@Email` (Hibernate Validator default — practical, RFC-aligned, handles 99 % of real addresses). Hibernate Validator's default normalises Unicode and rejects malformed inputs; reinventing this is a defect. |
| 8 | Password complexity rule for signup? | Answered | **Length-only** in MVP — `@Size(min = 12, max = 128)`. No mandatory character classes. Length-over-complexity matches NIST SP 800-63B 2024; an explicit ADR is not required because [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) does not mandate a complexity policy. Captured in CLAUDE.md so a future tightening is a documented change. |
| 9 | `base_currency` whitelist source — ISO 4217 full set, or a project-curated subset? | Answered | **`java.util.Currency.getAvailableCurrencies()`** (the JVM's ISO 4217 set). [../business-rules/core-wallet-management-rules.md FR1.1](../business-rules/core-wallet-management-rules.md#fr11--user-signup-and-wallet-opening) does not name a curated subset; the wallet table uses a CHECK constraint with an "ISO 4217 whitelist" but the actual list is not committed elsewhere. Using the JVM's set keeps the validator deterministic across environments and avoids hand-curating a list that drifts. The `wallet.currency` CHECK constraint in Phase 2 will narrow this to a project-decided subset; Phase 1's `@CurrencyCode` validator does not need to. |
| 10 | Email case-sensitivity on lookup + uniqueness? | Answered | **Case-insensitive.** Persist `email` in the user-provided casing, but normalise to `LOWER(email)` for the uniqueness check and for `findByEmail`. The Flyway migration adds `CREATE UNIQUE INDEX user_email_lower_uniq ON "user" (LOWER(email))`. Justified by [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) — enumeration resistance is harder if `Alice@x.com` and `alice@x.com` are separate accounts (one path returns `email_taken`, the other does not). |
| 11 | DB-constraint violation on duplicate email — map via Hibernate `ConstraintViolationException` or pre-check + service-raised conflict? | Answered | **Pre-check.** The service issues a `findByEmail` and raises `ConflictException("user.email_taken", ...)` before insert. Belt-and-braces: the DB unique index on `LOWER(email)` is the last line of defence, and the global mapper catches `org.hibernate.exception.ConstraintViolationException` and translates it to the same `user.email_taken` error key so a race (two concurrent signups with the same email) still surfaces correctly. |
| 12 | Test JWT keypair — generated per test run or committed fixture? | Answered | **Committed fixture** under `src/test/resources/META-INF/jwt-public-key-test.pem` + `jwt-private-key-test.pem`. The test private key is committed because the test set never runs against real users. The test profile in `src/test/resources/application.properties` points `mp.jwt.verify.publickey.location` and the issuer's private-key provider at the test fixtures via `%test.` overrides. |
| 13 | Should we add a `GET /me` introspection endpoint now to exercise the JWT verifier? | Answered | **No.** Master plan §9 lists exactly `POST /users` + `POST /auth/login` for Phase 1. The verifier wiring is exercised by an integration test (`JwtVerifierIT`) that mounts a throwaway `@Path("/_test/protected")` resource **only when the `test` profile is active**, and asserts both pass + fail paths. The resource is annotated `@io.quarkus.test.junit.QuarkusTest`-friendly via `quarkus.profile=test` guard and is never compiled into the production jar (placed under `src/test/java/`). |
| 14 | Do we ship `quarkus-flyway` now or wait for V2 in Phase 2? | Answered | **Now.** V1 is the first migration; Flyway must run on startup so the entity / repository tests are non-trivial. Adding it later forces a re-test of the Phase 1 surface. |
| 15 | RBAC at the service layer — does `UserService.signup` need a guard? | Answered | **No.** Signup is a public endpoint (no JWT); the service can be called only by `UserResource` and its test. RBAC guards land in Phase 2 (`WalletService.openWallet` is the first authenticated service). [../../.claude/rules/security.md §3](../../.claude/rules/security.md#3-authorization) "default-deny" applies from Phase 2 onwards. |

**Approval gate:** all rows Answered or explicitly Deferred. `/implement-plan docs/plans/implementation-plan-phase-1-signup-login.md` may run.

## 5. Technical Approach / Architecture Decisions

Phase 1 does **not** touch the synchronous money path described in [../../CLAUDE.md §Synchronous stream](../../CLAUDE.md#synchronous-stream-money-path); there is no wallet lock, no `PESSIMISTIC_WRITE`, no outbox, no fraud pre-check, no idempotency key. The flow is purely identity + token issuance + verification.

### Signup flow (`POST /users`)

```
JAX-RS resource (UserResource)
  └─ @Valid CreateUserRequest    [Bean Validation: @Email, @Size, @CurrencyCode]
  └─ UserService.signup(req)     [@Transactional]
        ├─ normalise email → LOWER(email)
        ├─ UserRepository.findByEmailLower(emailLower)
        │     └─ if present → throw ConflictException("user.email_taken")
        ├─ Argon2Hasher.hash(password) → "$argon2id$v=19$m=65536,t=3,p=1$<salt>$<hash>"
        ├─ UserEntity(id = UUID.randomUUID(), email, password_hash, role = USER,
        │             base_currency, fraud_status = ACTIVE, created_at = Instant.now(clock))
        ├─ UserRepository.persist(entity)
        │     └─ if DB unique-index violation → mapper translates to "user.email_taken"
        └─ return new CreateUserResponse(user.id, user.created_at)
  └─ HTTP 201
```

The Bouncy Castle `Argon2BytesGenerator` produces a 32-byte hash; the stored value is a self-describing string (`$argon2id$v=19$m=65536,t=3,p=1$<base64-salt>$<base64-hash>`) so parameters can evolve without a backfill. A `Argon2Hasher.verify(input, stored)` re-derives the hash using the stored parameters and compares with `MessageDigest.isEqual` for constant-time equality.

### Login flow (`POST /auth/login`)

```
AuthResource
  └─ @Valid LoginRequest         [Bean Validation: @Email, @NotBlank password]
  └─ AuthService.login(req)
        ├─ Optional<UserEntity> = UserRepository.findByEmailLower(email)
        ├─ if absent → Argon2Hasher.verify(req.password, SENTINEL_HASH); throw AuthInvalidCredentialsException
        ├─ if present:
        │     ├─ ok = Argon2Hasher.verify(req.password, user.password_hash)
        │     └─ if !ok → throw AuthInvalidCredentialsException
        ├─ token = JwtIssuer.issue(user.id, user.role, ttl = 3600s)
        └─ return new LoginResponse(token, "Bearer", 3600L)
  └─ HTTP 200
```

The branch on user absence runs the same Argon2id derivation against a fixed sentinel hash before throwing, so the wall-clock duration of the "user not found" branch matches the "wrong password" branch. The exception thrown is **identical** in both cases (`AuthInvalidCredentialsException` → `error_key: "auth.invalid_credentials"`, HTTP 401, identical message). This is the [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) "enumeration prevention" rule made literal.

### JWT issuance + verification

```
JwtIssuer (shared/security)
  └─ Jwt.claims()
       .subject(userId.toString())
       .issuer("digitalwallet")
       .audience("digitalwallet-api")
       .groups(Set.of(role.name()))
       .issuedAt(now)
       .expiresAt(now + ttl)
       .sign(privateKey)              // io.smallrye.jwt.build.Jwt
```

Verification is wired purely through SmallRye JWT — Quarkus reads `mp.jwt.verify.publickey.location`, `mp.jwt.verify.issuer`, `mp.jwt.verify.audiences`, enforces `alg = ES256` via a Quarkus property allow-list (`smallrye.jwt.verify.algorithm=ES256`), and exposes the principal via `@RolesAllowed`. No custom verifier code lands in this phase.

Clock skew tolerance ([../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication)): SmallRye's default is 60 s; we **tighten it to 30 s** via `mp.jwt.verify.token.age=` (NB: actually `smallrye.jwt.time-to-live` is unrelated — the skew property is `smallrye.jwt.expiration.grace=30`; verify the exact property name during implementation, the rule is the 30-second cap).

### Exception mapping

`DomainException` is a sealed abstract class permitting:

- `ValidationException("validation.invalid_payload", ...)` — 400.
- `IdempotencyKeyRequiredException` — 400. *(Permitted now; no caller until Phase 3.)*
- `AuthInvalidCredentialsException` — 401.
- `AuthForbiddenException` — 403. *(Permitted now; no caller until Phase 2.)*
- `ConflictException("user.email_taken", ...)` — 409. Also subclassed in later phases for `wallet.duplicate_label`, `idempotency.replay_conflict`, etc.
- `BusinessRuleException` — 422. *(Permitted now; no caller until Phase 3.)*
- `RateLimitException` — 429. *(Permitted now; no caller until Phase 6.)*
- `CircuitOpenException` — 503. *(Permitted now; no caller until Epic 6, deferred MVP-wide. Class shipped to keep the sealed `permits` list complete.)*

The single `DomainExceptionMapper` produces the canonical envelope from [../api/README.md §Error response shape](../api/README.md#error-response-shape):

```json
{ "error_key": "<dot.separated.identifier>", "message": "<human-readable>" }
```

`ConstraintViolationException` (from Hibernate Validator) is mapped to `validation.invalid_payload` with a `details` array of field violations.

### Module boundaries

Per [../../.claude/rules/backend_coding.md §1](../../.claude/rules/backend_coding.md#1-project-structure):

- `user/api/` may import `user/service/` + `shared/exception/` + `shared/security/UserRole`.
- `user/service/` may import `user/persistence/` + `shared/security/Argon2Hasher` + `shared/security/JwtIssuer` + `shared/exception/`.
- `user/persistence/` is JPA-only; no JAX-RS, no `shared/security/`.
- No `wallet/` / `fraud/` / `pfm/` / `advisor/` / `dashboard/` imports anywhere — none of those packages exist yet.

No Mermaid diagram is needed: the slice does not cross HTTP ↔ Kafka or backend ↔ frontend boundaries.

## 6. Applicable Rules, Skills & Agents

| Concern | Source |
|---|---|
| Module layout & feature-based + layered organisation | [../../.claude/rules/backend_coding.md §1](../../.claude/rules/backend_coding.md#1-project-structure) |
| Resource conventions, path constants, DTO returns | [../../.claude/rules/backend_coding.md §2](../../.claude/rules/backend_coding.md#2-routing--controllers) |
| Service layer: transaction boundary, RBAC, what must not run on the request thread | [../../.claude/rules/backend_coding.md §3](../../.claude/rules/backend_coding.md#3-service-layer) |
| Data models: UUID PK, `Instant`, `Currency`, lazy relations | [../../.claude/rules/backend_coding.md §4](../../.claude/rules/backend_coding.md#4-data-models--entities) |
| Repository style: Panache Repository pattern, `Optional` returns | [../../.claude/rules/backend_coding.md §5](../../.claude/rules/backend_coding.md#5-data-access) |
| DTO naming: `<Action><Noun>Request`, `<Noun>Response`, records | [../../.claude/rules/backend_coding.md §6](../../.claude/rules/backend_coding.md#6-dtos) |
| Exception envelope + status-code mapping table | [../../.claude/rules/backend_coding.md §8](../../.claude/rules/backend_coding.md#8-exception-handling) |
| Logging: SLF4J, placeholder syntax, forbidden content | [../../.claude/rules/backend_coding.md §11](../../.claude/rules/backend_coding.md#11-logging) |
| Validation: `@Valid` at the boundary, custom `@CurrencyCode` | [../../.claude/rules/backend_coding.md §12](../../.claude/rules/backend_coding.md#12-validation) |
| Flyway forward-only, V1 file name, entity + migration in same PR | [../../.claude/rules/backend_coding.md §13](../../.claude/rules/backend_coding.md#13-database-migrations), [../database/migrations.md](../database/migrations.md) |
| MicroProfile Config + `app.*` namespace; no committed secrets | [../../.claude/rules/backend_coding.md §17](../../.claude/rules/backend_coding.md#17-configuration), [../../.claude/rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration) |
| Java 21 idioms: records, sealed, constructor injection, `Clock` | [../../.claude/rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) |
| Authentication: ES256, Argon2id, enumeration resistance, 30 s skew | [../../.claude/rules/security.md §2](../../.claude/rules/security.md#2-authentication) |
| Secrets: env-var only, no committed defaults for `JWT_PRIVATE_KEY` | [../../.claude/rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration) |
| Sensitive data exposure: no `password_hash` in DTOs, no full JWT in logs | [../../.claude/rules/security.md §7](../../.claude/rules/security.md#7-sensitive-data-exposure) |
| Test contract: JUnit 5 + Mockito, Testcontainers Postgres 16, naming, exception assertions | [../../.claude/rules/testing.md §2](../../.claude/rules/testing.md#2-backend-testing) |
| Coverage gate: ≥ 80 % service-layer line (NFR4) | [../../.claude/rules/testing.md §1](../../.claude/rules/testing.md#1-coverage-targets), [../../CLAUDE.md](../../CLAUDE.md) Non-Negotiable Invariants |
| Security tests: unauthenticated, wrong-role, boundary, replay | [../../.claude/rules/security.md §11](../../.claude/rules/security.md#11-testing-security-sensitive-code) |
| ADR template + status flip procedure | [../decisions/template.md](../decisions/template.md), [../decisions/README.md](../decisions/README.md) |
| Backend vertical-slice scaffolding | `Skill("backend-create-rest-api")` |
| Backend unit-test scaffolding | `Skill("backend-create-unit-test")` |
| Local backend build pipeline | `Skill("backend-verify")` |
| PR review against the rules | `Skill("code-review")` |
| Implementation | `@backend-developer` |

`@frontend-developer` is **not** dispatched in this phase. `Skill("frontend-*")` is N/A.

## 7. File Structure

Files touched in this phase (cumulative — only what Phase 1 adds or modifies):

```
backend/
├── pom.xml                                            # modified (add quarkus-flyway + bouncycastle)
├── .env.example                                       # modified (add JWT_PRIVATE_KEY placeholder)
└── src/
    ├── main/
    │   ├── java/com/digitalwallet/
    │   │   ├── shared/
    │   │   │   ├── exception/
    │   │   │   │   ├── DomainException.java                       # sealed abstract base
    │   │   │   │   ├── ErrorResponse.java                         # record { errorKey, message }
    │   │   │   │   ├── ValidationException.java
    │   │   │   │   ├── IdempotencyKeyRequiredException.java       # ships for sealed permits
    │   │   │   │   ├── AuthInvalidCredentialsException.java
    │   │   │   │   ├── AuthForbiddenException.java                # ships for sealed permits
    │   │   │   │   ├── ConflictException.java
    │   │   │   │   ├── BusinessRuleException.java                 # ships for sealed permits
    │   │   │   │   ├── RateLimitException.java                    # ships for sealed permits
    │   │   │   │   ├── CircuitOpenException.java                  # ships for sealed permits
    │   │   │   │   ├── DomainExceptionMapper.java                 # JAX-RS ExceptionMapper
    │   │   │   │   ├── ConstraintViolationExceptionMapper.java
    │   │   │   │   └── package-info.java
    │   │   │   ├── security/
    │   │   │   │   ├── Argon2Hasher.java
    │   │   │   │   ├── JwtIssuer.java
    │   │   │   │   ├── JwtSigningKeyProvider.java
    │   │   │   │   ├── JwtSigningConfig.java                      # @ConfigMapping interface
    │   │   │   │   ├── UserRole.java                              # enum { USER, ADMIN }
    │   │   │   │   └── package-info.java
    │   │   │   ├── validation/
    │   │   │   │   ├── CurrencyCode.java                          # @interface
    │   │   │   │   ├── CurrencyCodeValidator.java
    │   │   │   │   └── package-info.java
    │   │   │   └── package-info.java                              # placeholder, may exist
    │   │   ├── user/
    │   │   │   ├── api/
    │   │   │   │   ├── UserResource.java                          # POST /users
    │   │   │   │   ├── AuthResource.java                          # POST /auth/login
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CreateUserRequest.java                 # record
    │   │   │   │   │   ├── CreateUserResponse.java                # record
    │   │   │   │   │   ├── LoginRequest.java                      # record
    │   │   │   │   │   ├── LoginResponse.java                     # record
    │   │   │   │   │   └── package-info.java
    │   │   │   │   └── package-info.java
    │   │   │   ├── service/
    │   │   │   │   ├── UserService.java
    │   │   │   │   ├── AuthService.java
    │   │   │   │   └── package-info.java
    │   │   │   ├── persistence/
    │   │   │   │   ├── UserEntity.java
    │   │   │   │   ├── UserRepository.java
    │   │   │   │   └── package-info.java
    │   │   │   └── package-info.java
    │   │   └── package-info.java
    │   └── resources/
    │       ├── application.properties                              # modified
    │       ├── META-INF/
    │       │   └── jwt-public-key-dev.pem                          # NEW (committed dev pubkey)
    │       └── db/
    │           └── migration/
    │               └── V1__create_user_table.sql                   # NEW
    └── test/
        ├── java/com/digitalwallet/
        │   ├── shared/
        │   │   ├── exception/
        │   │   │   └── DomainExceptionMapperTest.java
        │   │   ├── security/
        │   │   │   ├── Argon2HasherTest.java
        │   │   │   └── JwtIssuerTest.java
        │   │   └── validation/
        │   │       └── CurrencyCodeValidatorTest.java
        │   ├── user/
        │   │   ├── service/
        │   │   │   ├── UserServiceTest.java                        # JUnit 5 + Mockito (unit)
        │   │   │   └── AuthServiceTest.java
        │   │   └── api/
        │   │       ├── UserResourceIT.java                         # @QuarkusTest + RestAssured + Testcontainers
        │   │       ├── AuthResourceIT.java
        │   │       └── JwtVerifierIT.java                          # protected /_test/protected
        │   └── testsupport/
        │       ├── PostgresTestResource.java                       # @QuarkusTestResource
        │       └── TestProtectedResource.java                      # only in test classpath
        └── resources/
            ├── application.properties                              # %test overrides
            └── META-INF/
                ├── jwt-public-key-test.pem                         # NEW
                └── jwt-private-key-test.pem                        # NEW (test fixture)
docs/
├── decisions/
│   └── 0001-jwt-signing-algorithm.md                               # modified — Proposed → Accepted
└── plans/
    ├── implementation-plan-mvp-master.md                           # Phase 1 row checkbox
    └── implementation-plan-phase-1-signup-login.md                 # THIS FILE
CLAUDE.md                                                           # Project Status update
```

Files NOT touched in this plan are unchanged from Phase 0.

## 8. Files to Modify / Create

| Module | Path | Action | Layer |
|---|---|---|---|
| backend | `backend/pom.xml` | Modify | shared |
| backend | `backend/.env.example` | Modify | shared |
| backend | `backend/src/main/resources/application.properties` | Modify | shared |
| backend | `backend/src/main/resources/META-INF/jwt-public-key-dev.pem` | Create | shared |
| backend | `backend/src/main/resources/db/migration/V1__create_user_table.sql` | Create | migration |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/DomainException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/ErrorResponse.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/ValidationException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/IdempotencyKeyRequiredException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/AuthInvalidCredentialsException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/AuthForbiddenException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/ConflictException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/BusinessRuleException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/RateLimitException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/CircuitOpenException.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/DomainExceptionMapper.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/ConstraintViolationExceptionMapper.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/exception/package-info.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/Argon2Hasher.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/JwtIssuer.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/JwtSigningKeyProvider.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/JwtSigningConfig.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/UserRole.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/security/package-info.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/validation/CurrencyCode.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/validation/CurrencyCodeValidator.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/shared/validation/package-info.java` | Create | shared |
| backend | `backend/src/main/java/com/digitalwallet/user/persistence/UserEntity.java` | Create | persistence |
| backend | `backend/src/main/java/com/digitalwallet/user/persistence/UserRepository.java` | Create | persistence |
| backend | `backend/src/main/java/com/digitalwallet/user/persistence/package-info.java` | Create | persistence |
| backend | `backend/src/main/java/com/digitalwallet/user/service/UserService.java` | Create | service |
| backend | `backend/src/main/java/com/digitalwallet/user/service/AuthService.java` | Create | service |
| backend | `backend/src/main/java/com/digitalwallet/user/service/package-info.java` | Create | service |
| backend | `backend/src/main/java/com/digitalwallet/user/api/UserResource.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/AuthResource.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/dto/CreateUserRequest.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/dto/CreateUserResponse.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/dto/LoginRequest.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/dto/LoginResponse.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/dto/package-info.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/api/package-info.java` | Create | api |
| backend | `backend/src/main/java/com/digitalwallet/user/package-info.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/shared/exception/DomainExceptionMapperTest.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/shared/security/Argon2HasherTest.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/shared/security/JwtIssuerTest.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/shared/validation/CurrencyCodeValidatorTest.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/user/service/UserServiceTest.java` | Create | service |
| backend tests | `backend/src/test/java/com/digitalwallet/user/service/AuthServiceTest.java` | Create | service |
| backend tests | `backend/src/test/java/com/digitalwallet/user/api/UserResourceIT.java` | Create | api |
| backend tests | `backend/src/test/java/com/digitalwallet/user/api/AuthResourceIT.java` | Create | api |
| backend tests | `backend/src/test/java/com/digitalwallet/user/api/JwtVerifierIT.java` | Create | api |
| backend tests | `backend/src/test/java/com/digitalwallet/testsupport/PostgresTestResource.java` | Create | shared |
| backend tests | `backend/src/test/java/com/digitalwallet/testsupport/TestProtectedResource.java` | Create | shared |
| backend tests | `backend/src/test/resources/application.properties` | Create | shared |
| backend tests | `backend/src/test/resources/META-INF/jwt-public-key-test.pem` | Create | shared |
| backend tests | `backend/src/test/resources/META-INF/jwt-private-key-test.pem` | Create | shared |
| docs | `docs/decisions/0001-jwt-signing-algorithm.md` | Modify | docs |
| docs | `docs/plans/implementation-plan-mvp-master.md` | Modify | docs |
| docs | `CLAUDE.md` | Modify | docs |

`backend/src/test/java/com/digitalwallet/.gitkeep` (created in Phase 0) is removed when the first test file lands.

## 9. Progress Tracker

Phases are dependency-ordered. Each step ends in a runnable green state.

- [ ] **Step 1 — Dependency adds.** Update `backend/pom.xml`: add `io.quarkus:quarkus-flyway`, `org.bouncycastle:bcprov-jdk18on` (pin to the latest 1.x patch on PR day — no version range). Confirm `./mvnw -B test` from `backend/` still passes (no business code yet; surefire prints "No tests to run"). — `@backend-developer`
  - [ ] Plan ✓ (this plan)
  - [ ] Build

- [ ] **Step 2 — Flyway V1 migration.** Write `src/main/resources/db/migration/V1__create_user_table.sql` matching [../database/README.md `user`](../database/README.md): `id uuid PK`, `email varchar NOT NULL`, `password_hash varchar NOT NULL`, `role varchar NOT NULL CHECK (role IN ('USER','ADMIN')) DEFAULT 'USER'`, `base_currency char(3) NOT NULL`, `fraud_status varchar NOT NULL CHECK (fraud_status IN ('ACTIVE','SUSPENDED')) DEFAULT 'ACTIVE'`, `created_at timestamptz NOT NULL`. Add `CREATE UNIQUE INDEX user_email_lower_uniq ON "user" (LOWER(email))`. Table name is quoted (`"user"`). — `@backend-developer`

- [ ] **Step 3 — `shared/exception/`.** Write the sealed `DomainException` base, all subclasses (those used in Phase 1 + the permits list for forward sealed-hierarchy compliance), `ErrorResponse` record, `DomainExceptionMapper`, `ConstraintViolationExceptionMapper`. Per [../../.claude/rules/backend_coding.md §8](../../.claude/rules/backend_coding.md#8-exception-handling), the mapper sets HTTP status from the table in §8. Unit test the mapper directly. — `@backend-developer`

- [ ] **Step 4 — `shared/security/`.** Write `UserRole` enum (`USER`, `ADMIN`), `Argon2Hasher` with `hash(String)` + `verify(String, String)` using Bouncy Castle `Argon2BytesGenerator`, `JwtSigningConfig` (`@ConfigMapping(prefix = "app.jwt")` exposing `issuer`, `audience`, `ttlSeconds`, `privateKey`), `JwtSigningKeyProvider` (`@Startup` `@ApplicationScoped` parsing PEM once into `ECPrivateKey`), `JwtIssuer.issue(UUID userId, UserRole role)` returning the signed token. Unit-test all four classes. — `@backend-developer`, `Skill("backend-create-unit-test")`

- [ ] **Step 5 — `shared/validation/`.** Write `@CurrencyCode` annotation + `CurrencyCodeValidator` (rejects null, empty, length ≠ 3, code not in `java.util.Currency.getAvailableCurrencies()`). Unit-test boundary cases (lowercase / mixed-case input rejected — ISO 4217 requires uppercase per [../../docs/api/README.md §Conventions](../api/README.md)). — `@backend-developer`, `Skill("backend-create-unit-test")`

- [ ] **Step 6 — `user/persistence/`.** Write `UserEntity` mapped to `"user"` (escape via `@Table(name = "\"user\"")` or alias as `app_user`; pick one — [../database/README.md](../database/README.md) authorises either), `UserRepository extends PanacheRepositoryBase<UserEntity, UUID>` with `Optional<UserEntity> findByEmailLower(String)` using a named JPQL query that calls `LOWER(:email)`. No unit test (Panache repositories are integration-tested per [../../.claude/rules/testing.md §2.2](../../.claude/rules/testing.md#22-mocking-decision-matrix)). — `@backend-developer`

- [ ] **Step 7 — `user/service/UserService`.** `@Transactional` `signup(CreateUserRequest)` performing the flow in §5 above. Constructor injection only. Inject `Clock` (per [../../.claude/rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code)) for `created_at`. Throw `ConflictException("user.email_taken", ...)` on duplicate. Unit-test happy path + duplicate email + invalid currency (the last bubbles up from `@Valid` before the service is reached, so the service-level test asserts the service trusts upstream validation). — `@backend-developer`, `Skill("backend-create-unit-test")`

- [ ] **Step 8 — `user/service/AuthService`.** `login(LoginRequest)` performing the constant-time flow in §5 above. The sentinel hash is a `private static final String` computed in a `@Startup` method (so it is generated once with the active Argon2id parameters). Throw `AuthInvalidCredentialsException` on both branches with the **same** message. Unit-test: happy path, wrong password, unknown email, both negative branches return identical exception, total wall-clock for the two negative branches differs by ≤ 5 ms (a soft timing assertion — see Risks). — `@backend-developer`, `Skill("backend-create-unit-test")`

- [ ] **Step 9 — `user/api/UserResource` + `user/api/AuthResource`.** Path constants per [../../.claude/rules/backend_coding.md §2](../../.claude/rules/backend_coding.md#2-routing--controllers). `UserResource.signup` returns `Response.status(201).entity(...)`; `AuthResource.login` returns `Response.ok(...)`. DTO records. Both endpoints are public — no `@RolesAllowed`, no `@PermitAll` (the latter is the Quarkus default for unauthenticated paths; the rest of the resources will need `@RolesAllowed` from Phase 2 onwards). — `@backend-developer`, `Skill("backend-create-rest-api")`

- [ ] **Step 10 — `application.properties` rewire.** Add `quarkus.flyway.migrate-at-start=true` (dev/test/prod). Set `quarkus.smallrye-jwt.enabled=true` (flip from Phase 0). Set `mp.jwt.verify.publickey.location=META-INF/jwt-public-key-dev.pem`, `mp.jwt.verify.issuer=digitalwallet`, `mp.jwt.verify.audiences=digitalwallet-api`, `smallrye.jwt.expiration.grace=30`, `smallrye.jwt.require.named-principal=true`. Add the `app.jwt.*` block — `app.jwt.issuer=digitalwallet`, `app.jwt.audience=digitalwallet-api`, `app.jwt.ttl-seconds=3600`, `app.jwt.private-key=${JWT_PRIVATE_KEY:}` (empty default — fail-fast at startup if requested in `prod`). Set logger redactions for `password_hash` / `Authorization` / `Idempotency-Key` (Quarkus `%logger` filter — see [../../.claude/rules/backend_coding.md §11](../../.claude/rules/backend_coding.md#11-logging)). — `@backend-developer`

- [ ] **Step 11 — Test fixtures.** Generate the dev keypair (`openssl ecparam -name prime256v1 -genkey -noout -out /tmp/dev-private.pem && openssl ec -in /tmp/dev-private.pem -pubout -out src/main/resources/META-INF/jwt-public-key-dev.pem`); destroy the dev private key file from disk (it is never committed; the local developer keeps a copy in their `.env`). Generate a separate test keypair, commit **both** halves under `src/test/resources/META-INF/`. Write `src/test/resources/application.properties` with `%test.mp.jwt.verify.publickey.location=META-INF/jwt-public-key-test.pem` and `%test.app.jwt.private-key=<contents-of-test-private-pem-as-single-line>` — the test PEM is committed precisely so this works without env-var indirection in CI. Note this is a `%test`-only override; the prod path still requires the env var. — `@backend-developer`

- [ ] **Step 12 — Integration tests.** `UserResourceIT`: happy signup (201 + body), duplicate email (409 `user.email_taken`), invalid currency (400 `validation.invalid_payload`), missing field (400). `AuthResourceIT`: happy login (200 + non-empty `access_token`), wrong password (401 `auth.invalid_credentials`), unknown email (401 `auth.invalid_credentials`), both negative paths return the **same** envelope. `JwtVerifierIT`: hits the test-only `/_test/protected` resource — happy path with a freshly issued token (200), no token (401), `alg: none` token forged with the same `kid` (401), HS256 token forged with the public key as a symmetric secret (401 — the classic confusion attack), token whose `exp` is more than 30 s in the past (401). All ITs use `@QuarkusTestResource(PostgresTestResource.class)` for a Testcontainers Postgres 16. — `@backend-developer`, `Skill("backend-create-unit-test")`

- [ ] **Step 13 — Documentation.** Flip [docs/decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) **Proposed → Accepted** with decision text covering Open Q #10 of the master plan + Open Q #2–#5 of this plan. Update [CLAUDE.md](../../CLAUDE.md) "Project Status" line to record that Phase 1 ships, the JWT verifier is enabled, and ADR 0001 is Accepted. Tick Phase 1's **Plan** checkbox in [implementation-plan-mvp-master.md §9](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14). — `@backend-developer`

- [ ] **Step 14 — Local verify + push.** `./mvnw -B verify` from `backend/`; confirm JaCoCo ≥ 80 % on `com/digitalwallet/*/service/**`; confirm Flyway logs `V1` applied; confirm `target/site/jacoco/jacoco.xml` includes the new service classes. Push; one-job CI (backend) must be green. — `@backend-developer`, `Skill("backend-verify")`

- [ ] **Step 15 — `Skill("code-review")` against the diff.** Run the checklist in [../../.claude/rules/security.md §12](../../.claude/rules/security.md#12-code-review-checklist--critical); address any findings before merge. — orchestrator, `Skill("code-review")`

The PR ships one or two commits; CI must be green on the head commit before merge.

## 10. Acceptance Criteria

Every box must be true on the merged commit.

- [ ] `POST /users` with `{ email, password, base_currency }` returns `HTTP 201` and `{ user_id, created_at }`.
- [ ] `POST /users` with an existing email (case-insensitive) returns `HTTP 409` and `{ "error_key": "user.email_taken", "message": "..." }`.
- [ ] `POST /users` with a non-ISO-4217 `base_currency` returns `HTTP 400` and `{ "error_key": "validation.invalid_payload", "message": "...", "details": [...] }`.
- [ ] `POST /users` with `password` shorter than 12 characters returns `HTTP 400` `validation.invalid_payload`.
- [ ] `POST /auth/login` with the correct password returns `HTTP 200` and `{ access_token, token_type: "Bearer", expires_in: 3600 }`.
- [ ] `POST /auth/login` with the wrong password returns `HTTP 401` `{ "error_key": "auth.invalid_credentials", "message": "<fixed>" }`.
- [ ] `POST /auth/login` with an unknown email returns `HTTP 401` `auth.invalid_credentials` with **byte-identical** envelope to the wrong-password case.
- [ ] The signed JWT carries `sub = user.id` (UUID), `iss = "digitalwallet"`, `aud = ["digitalwallet-api"]`, `groups = ["USER"]`, `iat`, `exp = iat + 3600`.
- [ ] A throwaway protected endpoint (test-only) annotated `@RolesAllowed("USER")` accepts the issued token and rejects: missing token (401), `alg: none` token (401), HS256 token forged with the public key as secret (401), token expired > 30 s (401), token with wrong `iss` (401), token with wrong `aud` (401).
- [ ] The `user` table exists in Postgres after Flyway runs at startup; `\d "user"` shows every column in the master-plan list, including `fraud_status varchar NOT NULL DEFAULT 'ACTIVE'`.
- [ ] The unique index on `LOWER(email)` exists; `\d "user"` lists `user_email_lower_uniq`.
- [ ] `password_hash` is a self-describing Argon2id string starting with `$argon2id$v=19$m=65536,t=3,p=1$`.
- [ ] No response DTO returns `password_hash`, `fraud_status`, `role`, or `email` (signup response is `{ user_id, created_at }` only; login response is `{ access_token, token_type, expires_in }` only).
- [ ] `quarkus.smallrye-jwt.enabled=true` is set in `application.properties` (no longer disabled from Phase 0).
- [ ] JaCoCo line coverage on `com/digitalwallet/*/service/**` ≥ 80 % (NFR4); CI fails below the threshold.
- [ ] [docs/decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) Status field reads `Accepted`, the date matches the merge date, and the Decision section names ES256 + the operational specifics (public-key location, env-var private-key, 30 s skew, 3600 s TTL, `iss`/`aud` values).
- [ ] [CLAUDE.md](../../CLAUDE.md) "Project Status" mentions Phase 1 complete + JWT verifier enabled + ADR 0001 Accepted.
- [ ] Phase 1 row in [implementation-plan-mvp-master.md §9](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14) has its "Plan" checkbox ticked (the "Build" checkbox is ticked by `/implement-plan` when this phase merges).
- [ ] `./mvnw -B verify` from `backend/` exits 0 on a clean checkout; CI green on the head commit.
- [ ] `grep -R "System.out.println" backend/src/main/` returns no results; `grep -R "println" backend/src/main/` returns no results outside legitimate JSON parsing paths.
- [ ] No committed secret in the diff: `JWT_PRIVATE_KEY` is not set in any `.properties` file with a non-empty literal; `backend/.env.example` has only the env-var **name** (no real key).
- [ ] `gitleaks` pre-commit hook passes on every commit in the PR.

## 11. Security Considerations (MANDATORY)

Mapped against every applicable section of [../../.claude/rules/security.md](../../.claude/rules/security.md).

- **§1 secrets and configuration.** `JWT_PRIVATE_KEY` is the only new secret; resolved exclusively from the env var. `backend/.env.example` adds the variable name with an empty value. The dev public key under `src/main/resources/META-INF/jwt-public-key-dev.pem` is **not** secret (verification key). The test private key under `src/test/resources/META-INF/` is committed because it never authenticates a real user. No `console.log` / `System.out.println` in production code. `gitleaks` runs on every commit.
- **§2 authentication.** ES256, `alg=none` and HS256 explicitly rejected (Quarkus `smallrye.jwt.verify.algorithm=ES256`). 30-second clock skew (`smallrye.jwt.expiration.grace=30`). Argon2id with the parameters in Open Q #1. Enumeration-resistant login: identical envelope and constant-time path on both negative branches (`AuthService.login` runs Argon2id verify against a sentinel hash when the user is missing). Recovery flow is deferred. ADR 0001 Accepted. WS upgrade JWT validation deferred MVP-wide.
- **§3 authorization.** Signup and login are the two public endpoints permitted by the §3 default-deny rule. No protected endpoints land in this phase; from Phase 2 onwards every endpoint will need `@RolesAllowed`. The `UserRole` enum ships now so Phase 2 can use it directly. No role escalation path (signup hard-codes `role = USER`).
- **§4 input validation & injection.** `@Valid` on `CreateUserRequest` and `LoginRequest` (Bean Validation + `@Email` + `@Size` + `@CurrencyCode`). `UserRepository.findByEmailLower` uses a named JPQL parameter (no concatenation). No sort / filter parameters (no list endpoint in Phase 1). No `dangerouslySetInnerHTML` (no frontend).
- **§5 transport & CORS.** No CORS allow-list / security headers in Phase 1 — see Open Q #6 (deferred to F1). MVP HTTPS termination is at the nginx tier in Phase F1 / Phase 3 docker-compose.
- **§6 sessions & token handling (frontend).** N/A — no frontend.
- **§7 sensitive data exposure.** `CreateUserResponse` returns `{ user_id, created_at }` only; `LoginResponse` returns `{ access_token, token_type, expires_in }` only. `UserResource` and `AuthResource` never log the email, password, or hash. Logger configuration in `application.properties` adds a deny-list pattern for `password_hash`, `Authorization`, `Idempotency-Key`. The full JWT is never logged; if a debug log of a token is needed, only the first 8 characters and a salted hash are permitted ([../../.claude/rules/backend_coding.md §11](../../.claude/rules/backend_coding.md#11-logging)).
- **§8 rate limiting.** N/A — first rate limiter is `POST /transfers` in Phase 6. The `auth.rate_limited` envelope is reserved for that phase; this plan does not emit it. Progressive back-off on `POST /auth/login` stays `<!-- not-yet-adopted -->`.
- **§9 dependencies.** `quarkus-flyway` and `bcprov-jdk18on` are standard, actively maintained, no open Critical/High CVEs as of 2026-05-25 (verify on the PR day via the GitHub Actions dependency scan).
- **§10 secret scanning.** `gitleaks` pre-commit hook must pass; CI re-runs the scan. The dev public key file is `.pem` content that `gitleaks` may flag — the regex set must be tuned to allow `BEGIN PUBLIC KEY` / `BEGIN EC PUBLIC KEY` while still flagging `BEGIN PRIVATE KEY` / `BEGIN EC PRIVATE KEY` outside `src/test/resources/`. The test-fixture private key under `src/test/resources/META-INF/jwt-private-key-test.pem` is allow-listed via a `.gitleaksignore` entry scoped to that exact path.
- **§11 testing security-sensitive code.** Required tests for Phase 1:
  - Unauthenticated request to a protected endpoint → 401. `JwtVerifierIT`.
  - Wrong-role request → no Phase-1 endpoint exposes a role distinction; the test ships in Phase 2.
  - Wrong-tenant → no path parameter accepts a user id in Phase 1; not applicable until Phase 2.
  - Replay → no `Idempotency-Key` endpoint in Phase 1; first replay test in Phase 3.
  - Boundary → `validation.*` returns for malformed `base_currency`, malformed `email`, password < 12 / > 128.
  - XSS → N/A backend (no rendered HTML).
  - Rate limit → N/A Phase 1.
  - Outbox event on block → N/A Phase 1 (no fraud path).
- **§12 code-review checklist.** Items relevant to this PR (others N/A and N/A is recorded):
  - [ ] No secret material in the diff (env var only for `JWT_PRIVATE_KEY`; verifier key is public).
  - [ ] No `console.log` / `System.out.println`.
  - [ ] N/A — no mutating money endpoint in this PR.
  - [ ] No new endpoint needs `@RolesAllowed` (signup + login are public); Phase 2's first protected endpoint will. Both DTO and Zod alignment apply from Phase F2.
  - [ ] N/A — no owner-scoped path parameter in this PR.
  - [ ] `@Valid` on every body endpoint.
  - [ ] N/A — no sort / filter in this PR.
  - [ ] JPQL uses bound parameters (`findByEmailLower`).
  - [ ] No response DTO leaks `password_hash`, JWT secrets, internal counters, or stack traces.
  - [ ] Every error path returns a typed `error_key` from the table in [../../.claude/rules/backend_coding.md §8](../../.claude/rules/backend_coding.md#8-exception-handling).
  - [ ] N/A — no `dangerouslySetInnerHTML`.
  - [ ] N/A — no `VITE_*` env variable.
  - [ ] N/A — no LLM prompt.
  - [ ] N/A — `audit_log` deferred.
  - [ ] `gitleaks` pre-commit passes.
  - [ ] N/A — no rate-limit endpoint added in Phase 1.
  - [ ] N/A — no WebSocket endpoint.
  - [ ] Unauthenticated test added (`JwtVerifierIT`); wrong-role / wrong-tenant tests deferred to Phase 2.
  - [ ] N/A — no replay surface in Phase 1.
  - [ ] Dependency scan clean of new High / Critical CVEs.

## 12. Testing Strategy

Per [../../.claude/rules/testing.md](../../.claude/rules/testing.md).

### Unit tests (JUnit 5 + Mockito)

- **`Argon2HasherTest`** — hash + verify round-trip; `verify` returns false for wrong password; `verify` returns false for tampered hash string; constant-time `verify` uses `MessageDigest.isEqual`; behaviour on null inputs.
- **`JwtIssuerTest`** — issued token decodes (using SmallRye verifier directly) to the expected claims; `exp` is exactly `iat + ttlSeconds` with respect to the injected `Clock`; tampered token fails verification; issuer can be re-used (key is loaded once at startup).
- **`CurrencyCodeValidatorTest`** — accepts `USD`, `EUR`, `JPY`; rejects null, empty, `US`, `USDX`, `usd` (case-sensitive — ISO 4217 is uppercase), `XYZ` (not in `Currency.getAvailableCurrencies()`).
- **`DomainExceptionMapperTest`** — `ValidationException` → 400, `AuthInvalidCredentialsException` → 401, `ConflictException` → 409; envelope shape matches `[../api/README.md](../api/README.md#error-response-shape)`; Hibernate Validator `ConstraintViolationException` mapped to `validation.invalid_payload` + `details`.
- **`UserServiceTest`** — happy signup; duplicate email throws `ConflictException("user.email_taken")`; password is Argon2id-hashed (assert on the prefix); `created_at` uses the injected `Clock`; the repository is called with `findByEmailLower(emailLower)` (case-insensitive lookup).
- **`AuthServiceTest`** — happy login returns a non-empty token; wrong password throws `AuthInvalidCredentialsException` with the fixed message; unknown email throws `AuthInvalidCredentialsException` with the **same** fixed message; the sentinel verify is invoked in the unknown-email path (asserted via spy); the elapsed-time difference between the two negative branches is `≤ 5 ms` on the test runner (asserted with a generous bound — see Risks).

Coverage scope: `UserService` + `AuthService` + `Argon2Hasher` (in `shared/`, but reachable via the same JaCoCo pattern `com/digitalwallet/*/service/**` only if we ship `shared/security/Argon2Hasher` — note the JaCoCo include pattern matches `com/digitalwallet/<module>/service/**` and `shared/security/` is **not** under a `service/` sub-package. This is intentional: NFR4's 80 % floor is the feature-module service layer, not shared helpers. JaCoCo will still measure `shared/security/` and report it, but it does not count toward the gate. `UserService` and `AuthService` between them are the only two service-layer classes in Phase 1; both must clear ≥ 80 %).

### Integration tests (`@QuarkusTest` + Testcontainers Postgres 16 + RestAssured)

Per [../../.claude/rules/testing.md §2.4](../../.claude/rules/testing.md#24-test-db-setup--testcontainers-vs-in-memory-policy): `@QuarkusTestResource(PostgresTestResource.class)` starts a Postgres 16 container per test class. Flyway runs V1 on startup. No H2.

- **`UserResourceIT`** — happy signup; duplicate email after a first signup; invalid `base_currency`; missing `email` / `password` / `base_currency`; password shorter than 12.
- **`AuthResourceIT`** — pre-seeds one user via direct repository call (`@Inject UserRepository`), then logs in (happy / wrong password / unknown email). Asserts envelope byte-equality on the two negative branches.
- **`JwtVerifierIT`** — mounts `TestProtectedResource` (`@Path("/_test/protected")`, `@RolesAllowed("USER")`) — declared in `src/test/java/` so it never reaches the production jar. Asserts: unauthenticated → 401; happy token → 200; `alg: none` forged token → 401; HS256 token forged with the public key as secret → 401; expired token (> 30 s grace) → 401; token with wrong `iss` → 401; token with wrong `aud` → 401.

### NFR test contexts ([../../.claude/rules/testing.md §2.9](../../.claude/rules/testing.md#29-required-nfr-test-contexts))

- **NFR1 (hybrid concurrency)** — N/A Phase 1 (no wallet mutation).
- **NFR2 (outbox)** — N/A Phase 1 (no outbox row).
- **NFR3 (idempotency)** — N/A Phase 1 (no mutating money endpoint).
- **NFR4 (coverage)** — JaCoCo gate enforced.
- **NFR5 (latency isolation)** — N/A.
- **NFR6 / NFR7 / NFR8 / NFR9** — N/A or deferred MVP-wide.

### Coverage floor

≥ 80 % line coverage on `com/digitalwallet/*/service/**` enforced by JaCoCo. Phase 1 introduces the first two service classes; both must individually clear 80 % or the cumulative bundle ≥ 80 %.

### Frontend tests

N/A this phase. Phase F2 owns the auth UI + Vitest specs.

## 13. Reference Files

Read in this order before opening the first PR commit:

- [implementation-plan-mvp-master.md §9 Epic 1 backend](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14) — Phase 1's row, exactly the scope this plan expands.
- [implementation-plan-phase-0-layout-reconcile.md](implementation-plan-phase-0-layout-reconcile.md) — the layout Phase 1 builds on.
- [../../CLAUDE.md](../../CLAUDE.md) — invariants and module layout; "Project Status" is the line Phase 1 updates.
- [../../.claude/rules/backend_coding.md](../../.claude/rules/backend_coding.md) — §§1, 2, 3, 4, 5, 6, 8, 11, 12, 13, 17.
- [../../.claude/rules/security.md](../../.claude/rules/security.md) — §§1, 2, 3, 4, 7, 11, 12.
- [../../.claude/rules/testing.md](../../.claude/rules/testing.md) — §§1, 2, 6.
- [../../.claude/rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) — Java 21 idioms.
- [../api/README.md Epic 1](../api/README.md) — `POST /users` and `POST /auth/login` rows.
- [../database/README.md `user`](../database/README.md) — exact column shape.
- [../database/migrations.md](../database/migrations.md) — Flyway naming + forward-only policy.
- [../business-rules/core-wallet-management-rules.md FR1.1](../business-rules/core-wallet-management-rules.md#fr11--user-signup-and-wallet-opening) — immutability of `base_currency`, the failure-mode table.
- [../decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) — to be flipped Accepted.
- [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md) — two-role model, `audit_log` deferred.
- [../architecture/README.md §6 Auth flow](../architecture/README.md#6-auth-flow), [§7 Config & profiles](../architecture/README.md#7-config--profiles) — JWT scheme + canonical env-var names.

## 14. Risks & Dependencies

### Risks

- **Argon2id parameters too aggressive for CI.** `memoryAsKB = 65536` (64 MiB) per hash on the AuthService's two paths (real verify + sentinel verify) doubles memory pressure. Mitigation: a `%test.app.argon2.memory-kb=4096` override drops the test profile to 4 MiB so CI runners stay snappy; production keeps the OWASP-grade 64 MiB. The `Argon2Hasher` reads these parameters from `@ConfigMapping` so the override is one property.
- **Timing-side-channel test is flaky.** Wall-clock comparisons on shared CI runners are not deterministic. Mitigation: the timing assertion in `AuthServiceTest` uses a 30 ms upper bound (generous), not a tight one — the goal is "same order of magnitude", not "byte-identical timing". The hard guarantee is in the code path (both branches run Argon2id once); the timing assertion is a smoke check that nobody short-circuited it.
- **`smallrye.jwt.verify.algorithm` property name drift.** SmallRye occasionally renames properties between Quarkus minors. Mitigation: the implementer verifies the property name against the Quarkus version pinned in Phase 0 (`./mvnw quarkus:list-extensions` + Quarkus SmallRye JWT docs for that minor). If renamed, the rule is unchanged — only the property key updates.
- **`mp.jwt.verify.publickey.location` and Flyway interaction at startup order.** Flyway runs early; SmallRye JWT key loading is independent. No ordering issue expected. Mitigation: an integration test (`UserResourceIT`) runs end-to-end against a fresh Postgres container — if either subsystem failed to start, the test fails on the first request.
- **Quoting `"user"` as the table name complicates Panache queries.** Native SQL fragments would need to quote it consistently. Mitigation: annotate the entity `@Table(name = "\"user\"")` and prefer Panache `find` / named JPQL queries — Hibernate auto-quotes the alias. If queries get hostile, fall back to `@Table(name = "app_user")` and run a renaming migration. [../database/README.md](../database/README.md) authorises both.
- **CI runner does not have `openssl`.** Step 11 generates the dev keypair off-PR (it is a one-shot developer task); the test keypair is committed. If the next developer regenerates the dev key, a script under `scripts/` would be welcome but is not part of this phase. Mitigation: README addendum (or follow-up plan) ships a `scripts/generate-jwt-dev-keypair.sh` later; for now the commands are inlined in Step 11.
- **Sentinel Argon2 hash leaks at startup (logged accidentally).** Mitigation: `JwtIssuer` and `AuthService` `@Startup` methods log only `"sentinel hash initialised"`, never the value. Reviewer checks the log lines in `AuthService` + `Argon2Hasher` in code review.
- **The `JwtVerifierIT` mounts a resource in test scope only — Quarkus needs the resource visible to JAX-RS in tests.** Quarkus dev/test mode scans `src/test/java/` for JAX-RS resources by default. Mitigation: confirmed Quarkus behaviour; the resource is annotated `@io.quarkus.test.junit.QuarkusTest`-internal via package + `@Path("/_test/...")` so it cannot collide with production paths.

### Dependencies

- **External:** Postgres 16 (Testcontainers) and JDK 21. No Docker Compose run required for tests (Testcontainers spins Postgres on demand). No Redis, no Kafka, no LLM dependency in Phase 1.
- **Internal cross-plan:** Phase 0 is a hard prerequisite (renamed `backend/`, LTS Quarkus, JaCoCo wired, Hibernate Validator + SmallRye JWT dependencies on classpath).
- **ADRs that move:** ADR 0001 (JWT signing algorithm) — Proposed → Accepted on Step 13. No other ADRs move.
- **Downstream consumers:** Phase 2 (open wallet) needs (a) the `user` table FK target, (b) `UserRole` enum, (c) the `shared/exception/` mapper for `auth.forbidden` + `wallet.duplicate_label`, (d) the JWT verifier wired so `@RolesAllowed("USER")` works. All four are first-class deliverables of this phase.

---

**Next step (after approval):** Run `/implement-plan docs/plans/implementation-plan-phase-1-signup-login.md`. The implementer starts at Step 1 (dependency adds) and works through Step 15 (`Skill("code-review")`). When the PR merges, this plan's "Build" checkbox in [implementation-plan-mvp-master.md §9](implementation-plan-mvp-master.md#epic-1-backend-fr11--fr14) is ticked and Phase 2 (`/make-plan phase-2-open-wallet`) begins.

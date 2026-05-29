# Implementation Plan: Phase 0 — Layout Reconcile

- **Date:** 2026-05-25
- **Ticket:** n/a (derived from [implementation-plan-mvp-master.md §9 Foundation](implementation-plan-mvp-master.md#foundation))
- **Story points:** S — single PR; mechanical rename + build-config additions, no business code.
- **Milestone:** MVP Epic 1 — Phase 0 foundation gate ([implementation-plan-mvp-master.md §2(implementation-plan-mvp-master.md))
- **Assignees:** unassigned
- **Affected modules (backend):** root build (`pom.xml`, `mvnw`, `application.properties`), Quarkus starter package rename `org.acme` → `com.digitalwallet`. No feature module code yet.
- **Affected modules (frontend):** none (returns in Phase F1).
- **Affected infrastructure:** `.github/workflows/ci.yml` (drop `frontend` + `docker-images` jobs temporarily; keep them runnable when Phase F1 / Phase 3 ship).
- **Suggested branch name:** `chore/phase-0-layout-reconcile`

---

## 2. Context / Problem Statement

The repository diverges from the canonical layout committed in [../../CLAUDE.md](../../CLAUDE.md) and [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering):

- `digital-wallet-api/` is a Quarkus 3.35.4 starter (`org.acme.GreetingResource` + tests) — wrong directory name, wrong root package, wrong Quarkus version against the LTS baseline mandated in [../rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines).
- `backend/` does not exist; every rule file, every doc, and `.github/workflows/ci.yml` already point to `backend/` and would break on first run.
- `frontend/` was removed in commit `5759fbb` and will only return in Phase F1; the existing CI has a `frontend` job + a `docker-images` job that both reference paths that do not exist.
- `pom.xml` is missing the dependencies required by every later phase (JaCoCo with the NFR4 gate, Testcontainers Postgres + JUnit Jupiter, Hibernate Validator, SmallRye JWT for REST, Quarkus Micrometer + Prometheus). `quarkus.hibernate-orm.database.generation` is unset (defaults to `none` in Quarkus, but [../rules/backend_coding.md §13](../../.claude/rules/backend_coding.md#13-database-migrations) requires it to be **explicitly** set — Flyway forward-only is the contract).
- `application.properties` uses env var names (`DB_USERNAME`, `DB_HOST`, `DB_PORT`, `DB_NAME`) that disagree with the canonical names in [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) (`DB_USER`, `DB_URL`).

**Desired end state.** After this PR merges:

- The directory `backend/` exists at the repo root and contains everything that was in `digital-wallet-api/` (Maven wrapper, `pom.xml`, `src/`, `.mvn/`).
- The Quarkus version is the **most recent 3.x LTS available at implementation time** (Quarkus ships LTS lines roughly every 6 months — 3.2, 3.8, 3.15, 3.20, …). The implementer MUST consult [https://quarkus.io/blog/tag/release/](https://quarkus.io/blog/tag/release/) on the PR day, pick the highest LTS minor whose latest patch is GA, and pin it. As of 2026-05-25 the latest LTS is **`3.33.2`** (3.33 LTS released 2026-03-25; 3.33.2 is the latest patch). The starter's 3.35.4 is non-LTS and not on the supported LTS line; CLAUDE.md's "Quarkus 3.15.6 LTS" wording is stale and MUST be updated in the same PR (see Step 4 + Step 7).
- The root Java package is `com.digitalwallet`; `GreetingResource` and its two test classes (`GreetingResourceTest`, `GreetingResourceIT`) are deleted.
- `pom.xml` declares: JaCoCo with a `≥ 80 %` line-coverage gate scoped to `com/digitalwallet/*/service/**` (NFR4); Testcontainers BOM + `postgresql` + `junit-jupiter`; `quarkus-hibernate-validator`; `quarkus-smallrye-jwt` + `quarkus-smallrye-jwt-build` (REST issuance + verification — WS upgrade deferred per [implementation-plan-mvp-master.md §2](implementation-plan-mvp-master.md)); `quarkus-micrometer-registry-prometheus`.
- `application.properties` sets `quarkus.hibernate-orm.database.generation=none` explicitly and uses the canonical env-var names from [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles).
- `.github/workflows/ci.yml` keeps only the `backend` job; the `frontend` and `docker-images` jobs are removed (they return in Phase F1 and Phase 3 respectively).
- `./mvnw -B verify` runs green from `backend/` on CI with the JaCoCo gate present but vacuously passing (no `com/digitalwallet/*/service/**` classes exist yet — the JaCoCo gate's `haltOnFailure` must not trip when there is nothing to measure).

No feature code is written in this phase. This is a pure reconcile-and-bootstrap PR; the next phase (Phase 1 — Signup + Login) introduces the first vertical slice.

## 3. Scope

### In Scope

- Rename `digital-wallet-api/` → `backend/` via `git mv` (preserve history).
- Bump Quarkus to the **latest 3.x LTS** in `pom.xml` (`quarkus.platform.version`). The implementer pins the exact version on the PR day after checking [https://quarkus.io/blog/tag/release/](https://quarkus.io/blog/tag/release/); confirmed version as of 2026-05-25 is `3.33.2` (3.33 LTS). CLAUDE.md's stale "Quarkus 3.15.6 LTS" reference is updated in the same PR.
- Set Maven coordinates to `com.digitalwallet:backend:1.0.0-SNAPSHOT` (`<groupId>` + `<artifactId>`).
- Rename root package `org.acme` → `com.digitalwallet` (move the source tree to `src/main/java/com/digitalwallet/`, `src/test/java/com/digitalwallet/`).
- Delete `GreetingResource.java`, `GreetingResourceTest.java`, `GreetingResourceIT.java`.
- Add to `pom.xml`:
  - `jacoco-maven-plugin` pinned to **`0.8.12`** (the latest 0.8.x patch known stable on Java 21 as of 2026-05-25; the implementer MAY bump to a newer 0.8.x on the PR day after a quick `mvn versions:display-plugin-updates` check, but MUST commit the exact version — no floating range). Wired with `prepare-agent`, `report`, and `check` goals. The `check` rule enforces `≥ 80 %` line coverage on `com/digitalwallet/*/service/**` (NFR4). `haltOnFailure` true.
  - Testcontainers BOM (`org.testcontainers:testcontainers-bom`) + `testcontainers:postgresql` + `testcontainers:junit-jupiter` at test scope.
  - `quarkus-hibernate-validator`.
  - `quarkus-smallrye-jwt` + `quarkus-smallrye-jwt-build` (REST issuance + verification only; WS upgrade deferred per [implementation-plan-mvp-master.md §2](implementation-plan-mvp-master.md)).
  - `quarkus-micrometer-registry-prometheus`.
- Set `quarkus.hibernate-orm.database.generation=none` in `src/main/resources/application.properties`.
- Bring the DB block from [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) — env vars `DB_URL`, `DB_USER`, `DB_PASSWORD`. Add `quarkus.redis.hosts=${REDIS_URL:redis://localhost:6379}` (used from Phase 3 onwards; defaulting now avoids reconfig later). Add `app.jwt.public-key=${JWT_PUBLIC_KEY}` and `quarkus.smallrye-jwt.enabled=true` as the JWT verifier placeholder (the actual key material lands in Phase 1).
- Update `.github/workflows/ci.yml`:
  - Keep `backend` job (already targets `working-directory: backend`).
  - Remove `frontend` job (returns in Phase F1).
  - Remove `docker-images` job (returns when `backend/Dockerfile` + `backend/docker-compose.yml` ship in Phase 3, and when `frontend/Dockerfile` ships in Phase F1).
- Add a one-line `# Phase 0 placeholder — Dockerfile arrives with docker-compose in Phase 3.` note via a comment in the workflow header so the reason for the removal is greppable.
- Confirm `./mvnw -B verify` is green on CI.

### Out of Scope

- Any `com.digitalwallet.*` feature module (`shared/`, `user/`, `wallet/`) — Phase 1+.
- Flyway migrations or any `db/migration/` SQL — first migration lands in Phase 1 (V1 user table).
- `backend/Dockerfile`, `backend/docker-compose.yml`, `backend/postgres/init/` — Phase 3.
- Quarkus dev-services for Postgres / Redis / Kafka — explicitly disabled in `application.properties` from Phase 1 onwards via Testcontainers config; not needed in Phase 0.
- ADR 0001 (JWT) status flip — happens in Phase 1 when ES256 keypair generation + verifier configuration lands.
- ADR 0011 (Observability) status flip — happens in Phase 8.
- Any code that touches Kafka / SmallRye Reactive Messaging — deferred until the first consumer ships post-MVP per [implementation-plan-mvp-master.md §2](implementation-plan-mvp-master.md).
- Frontend bootstrap — Phase F1.

## 4. Open Questions (MANDATORY)

Status legend: **Unanswered (open)** | **Answered (closed)** | **Deferred (tracked elsewhere)**

| # | Question | Status | Resolution / Owner |
|---|---|---|---|
| 1 | Which Quarkus version do we target — the 3.35.4 already on disk, the 3.15.6 LTS named in CLAUDE.md, or the most recent LTS? | Answered | **`3.33.2`** (3.33 LTS, released 2026-03-25 — confirmed 2026-05-25). CLAUDE.md's "Quarkus 3.15.6 LTS" wording is stale and is updated in the same PR (Step 4 + Step 7). The starter's 3.35.4 is non-LTS; [../rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines) mandates Quarkus 3.x LTS. |
| 2 | Should we delete `GreetingResource` (+ tests) now or keep it for one sanity-check verify? | Answered | Delete now. It would force a JaCoCo carve-out (the gate is scoped to `com/digitalwallet/*/service/**`, which excludes it anyway, but the deleted dead code keeps the diff smaller and avoids `org.acme` leftovers). |
| 3 | Do we keep the maven-failsafe-plugin block from the starter, or wait until Phase 3 (the first integration test)? | Answered | Keep it. It is no-op when there are no `*IT.java` classes, and removing it would force a re-add in Phase 3 — churn without value. |
| 4 | Does the JaCoCo `check` rule trip on a fresh repo with zero matched classes? | Answered | No — JaCoCo `check` on a non-existent package counts as zero classes matched and the rule passes vacuously. This is confirmed behaviour; Phase 1's first service class is the first time the gate has anything to measure. |
| 5 | Do we keep the temporary `frontend` + `docker-images` CI jobs disabled in-place (commented), or remove them outright? | Answered | Remove outright. Phase F1 / Phase 3 will re-add them in the exact shape they need; commented-out CI rots silently. The header comment on `ci.yml` already documents the gate set, so the absence is self-explanatory. |
| 6 | Maven `groupId`/`artifactId` after rename — `com.digitalwallet:backend` or `com.digitalwallet:digital-wallet`? | Answered | `com.digitalwallet:backend`. Mirrors the directory name and matches the layout tree in [../../CLAUDE.md](../../CLAUDE.md) §Module layout. |
| 7 | Do we add `quarkus-smallrye-jwt-build` now, or wait until Phase 1 needs token issuance? | Answered | Add it now. Splitting the dependency move across two PRs is churn; the artifact has zero runtime footprint until a `JWTBuilder` is wired. |
| 8 | `quarkus.hibernate-orm.database.generation` value? | Answered | `none`. Mandated by [../rules/backend_coding.md §13](../../.claude/rules/backend_coding.md#13-database-migrations). |
| 9 | JaCoCo plugin version — float or pin? | Answered | **Pin to `0.8.12`** (or the latest stable 0.8.x patch on the PR day, committed as a literal — no `LATEST` / no `[0.8,)` ranges). Floating versions break reproducible builds and make CI drift silent. |

**Approval gate:** all rows Answered. `/implement-plan` may run.

## 5. Technical Approach / Architecture Decisions

This phase does no business work. The architecture decision is to land the canonical project layout from [../../CLAUDE.md](../../CLAUDE.md) §Module layout in a single PR so every subsequent phase can branch from a directory layout that already matches every rule file.

Key choices:

- **Rename via `git mv`** preserves blame history on `pom.xml`, `mvnw`, and the resource files. A `rm -rf` + recreate would discard the starter's blame.
- **One PR, not two.** The CI references `backend/` and the working directory contains `digital-wallet-api/` — every commit in between is broken. Splitting rename / pom-bump / CI-update across PRs guarantees a red main. Land it atomically.
- **JaCoCo gate scoped to `com/digitalwallet/*/service/**`.** Matches the NFR4 wording in [../../CLAUDE.md](../../CLAUDE.md) Non-Negotiable Invariants and [../rules/testing.md §1](../../.claude/rules/testing.md#1-coverage-targets). DO NOT scope the gate to `com/digitalwallet/**` — controllers and DTOs are excluded from the 80 % floor by design.
- **Quarkus extensions used over bare libraries.** `quarkus-smallrye-jwt` + `quarkus-smallrye-jwt-build`, `quarkus-hibernate-validator`, `quarkus-micrometer-registry-prometheus` — required by [../rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) ("Quarkus extensions over raw libraries").
- **No `synchronized`, no field injection, no `javax.*` enters in this PR** — the starter is on `jakarta.*` already, and there is no business code to violate the rules. Stays compliant by construction with [../rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code).
- **CI: keep one job green; remove the two that point at not-yet-existing artifacts.** Keeping commented-out YAML invites silent rot. Phase F1 and Phase 3 own the re-introduction in the exact form they need.

No diagram needed — no module boundary crossings change in this phase.

## 6. Applicable Rules, Skills & Agents

| Concern | Source |
|---|---|
| Build tool baseline (Maven) | [../../docs/decisions/0007-build-tool.md](../../docs/decisions/0007-build-tool.md) |
| Java / Quarkus / LTS baseline | [../rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines) |
| Quarkus-extension-first policy | [../rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) |
| Package namespace (`jakarta.*`, no `javax.*`) | [../rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) |
| `quarkus.hibernate-orm.database.generation=none` | [../rules/backend_coding.md §13](../../.claude/rules/backend_coding.md#13-database-migrations) |
| JaCoCo ≥ 80 % service-layer gate (NFR4) | [../../CLAUDE.md](../../CLAUDE.md) Non-Negotiable Invariants, [../rules/testing.md §1](../../.claude/rules/testing.md#1-coverage-targets) |
| `application.properties` env-var naming | [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) |
| CI gate set (compile + verify + JaCoCo + frontend lint/test) | [../rules/testing.md §4.2](../../.claude/rules/testing.md#42-full-ci) |
| No committed secrets, env-var loading | [../rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration) |
| Local backend build pipeline | `Skill("backend-verify")` |
| Backend implementation | `@backend-developer` |

`Skill("backend-create-rest-api")` and `Skill("backend-create-unit-test")` are NOT invoked in this phase — no resource, no service, no test class is created.

## 7. File Structure

ASCII tree of every directory touched. After Phase 0 lands, the cumulative layout (Phase 0 contribution only) is:

```
DigitalWallet/
├── .github/
│   └── workflows/
│       └── ci.yml                         # modified (drop frontend + docker-images jobs)
├── backend/                               # renamed from digital-wallet-api/
│   ├── .dockerignore                      # carried over (harmless; used in Phase 3)
│   ├── .gitignore                         # carried over
│   ├── .mvn/                              # carried over
│   ├── mvnw                               # carried over
│   ├── mvnw.cmd                           # carried over
│   ├── pom.xml                            # modified (Quarkus 3.15.6, groupId/artifactId, deps, JaCoCo)
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── digitalwallet/     # renamed from org/acme; empty (placeholder only)
│       │   │           └── package-info.java   # one-liner; see §8
│       │   └── resources/
│       │       └── application.properties  # rewritten — see §8
│       └── test/
│           └── java/
│               └── com/
│                   └── digitalwallet/     # empty (Phase 1 fills it)
│                       └── .gitkeep
└── docs/
    └── plans/
        └── implementation-plan-phase-0-layout-reconcile.md   # this file (already created)
```

Files NOT in the tree above are not touched.

The `digital-wallet-api/.env` and `digital-wallet-api/.env.example` files move with `git mv` to `backend/.env` and `backend/.env.example`; the `.env` value is local-only (the existing `.gitignore` keeps it out). The committed `.env.example` is rewritten to use the canonical env-var names (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_URL`).

The starter `digital-wallet-api/README.md` is moved to `backend/README.md` and trimmed to a single line pointing at the master plan — the original starter content is post-rename noise.

## 8. Files to Modify / Create

| Module | Path | Action | Layer |
|---|---|---|---|
| repo | `digital-wallet-api/` | Delete (via `git mv` to `backend/`) | shared |
| repo | `backend/` | Create (via `git mv` from `digital-wallet-api/`) | shared |
| backend | `backend/pom.xml` | Modify (groupId → `com.digitalwallet`, artifactId → `backend`, Quarkus → 3.15.6, add JaCoCo + Testcontainers + Hibernate Validator + SmallRye JWT + Micrometer Prometheus) | shared |
| backend | `backend/src/main/java/org/acme/GreetingResource.java` | Delete | api |
| backend | `backend/src/test/java/org/acme/GreetingResourceTest.java` | Delete | shared |
| backend | `backend/src/test/java/org/acme/GreetingResourceIT.java` | Delete | shared |
| backend | `backend/src/main/java/org/acme/` | Delete (after files removed) | shared |
| backend | `backend/src/test/java/org/acme/` | Delete (after files removed) | shared |
| backend | `backend/src/main/java/com/digitalwallet/package-info.java` | Create (one-line `package com.digitalwallet;` placeholder so the `main/java` tree is non-empty for Maven and IDEs) | shared |
| backend | `backend/src/test/java/com/digitalwallet/.gitkeep` | Create (so the test tree is non-empty and tracked) | shared |
| backend | `backend/src/main/resources/application.properties` | Modify (rewrite to canonical env-var names + `quarkus.hibernate-orm.database.generation=none` + JWT public-key placeholder) | shared |
| backend | `backend/.env.example` | Modify (rename `DB_USERNAME`/`DB_HOST`/`DB_PORT`/`DB_NAME` to `DB_URL`/`DB_USER`/`DB_PASSWORD`, add `REDIS_URL`, add `JWT_PUBLIC_KEY` + `JWT_PRIVATE_KEY` as commented-out placeholders) | shared |
| backend | `backend/README.md` | Modify (replace starter content with a one-paragraph "see [docs/plans/implementation-plan-mvp-master.md](../docs/plans/implementation-plan-mvp-master.md)") | docs |
| ci | `.github/workflows/ci.yml` | Modify (remove `frontend` + `docker-images` jobs; keep `backend`; update header comment) | docs |

## 9. Progress Tracker

Phases inside this plan are dependency-ordered. Each step ends in a runnable green state.

- [ ] **Step 1 — `git mv digital-wallet-api backend`.** Verify history preserved (`git log --follow backend/pom.xml`). Verify `./mvnw -B compile` from `backend/` still works against the starter. — `@backend-developer`
- [ ] **Step 2 — Delete `GreetingResource` + the two test classes.** Verify `./mvnw -B test` from `backend/` is green (zero tests run is the expected state). — `@backend-developer`
- [ ] **Step 3 — Package rename `org.acme` → `com.digitalwallet`.** Move `src/main/java/org/acme/` → `src/main/java/com/digitalwallet/`, `src/test/java/org/acme/` → `src/test/java/com/digitalwallet/`. After Step 2 the only file left is the package-info placeholder + `.gitkeep`; create them in this step. — `@backend-developer`
- [ ] **Step 4 — `pom.xml` rewrite.** Set `groupId` = `com.digitalwallet`, `artifactId` = `backend`; bump `quarkus.platform.version` to `3.33.2` (3.33 LTS — confirmed 2026-05-25); add `jacoco-maven-plugin` pinned to `0.8.12` (or the latest 0.8.x on the PR day, again pinned literally) with the gate scoped `com/digitalwallet/*/service/**`, `haltOnFailure=true`; add Testcontainers BOM + `postgresql` + `junit-jupiter` (test scope); add `quarkus-hibernate-validator`, `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-micrometer-registry-prometheus`. — `@backend-developer`
- [ ] **Step 5 — `application.properties` rewrite.** Set `quarkus.hibernate-orm.database.generation=none`; use `DB_URL`/`DB_USER`/`DB_PASSWORD` (canonical names); add `quarkus.redis.hosts=${REDIS_URL:redis://localhost:6379}`; add `mp.jwt.verify.publickey=${JWT_PUBLIC_KEY:}` + `mp.jwt.verify.issuer=${JWT_ISSUER:digitalwallet-dev}` (the actual key material arrives in Phase 1; an empty default keeps the build green). — `@backend-developer`
- [ ] **Step 6 — `.env.example` and `README.md` cleanup.** Bring `.env.example` in line with the canonical names; trim `README.md` to a master-plan pointer. — `@backend-developer`
- [ ] **Step 7 — `.github/workflows/ci.yml` update and CLAUDE.md fix.** Drop `frontend` and `docker-images` jobs from `ci.yml`; update the header comment to record the gate set in effect for MVP Phase 0–Epic 1 (compile + verify + JaCoCo). Update the stale "Quarkus 3.15.6 LTS" wording in [../../CLAUDE.md](../../CLAUDE.md) "Project Status" to the LTS version actually pinned in Step 4. — `@backend-developer`
- [ ] **Step 8 — Local verify.** Run `./mvnw -B verify` from `backend/`. Confirm the JaCoCo report writes to `backend/target/site/jacoco/`. Confirm the JaCoCo `check` rule passes vacuously (zero classes matched). — `@backend-developer`, `Skill("backend-verify")`
- [ ] **Step 9 — Push, watch CI go green.** Open PR; one-job CI (backend only) MUST be green before merge. — orchestrator

The PR ships exactly one commit (or a clean fast-forward — preserve `git mv` history). No squash that would lose the rename signal.

## 10. Acceptance Criteria

Every box below MUST be true on the merged commit. Reviewer can sign off PASS/FAIL without judgement.

- [ ] `digital-wallet-api/` does not exist at the repo root; `backend/` does.
- [ ] `git log --follow backend/pom.xml` shows continuous history (the `git mv` preserved blame).
- [ ] `backend/pom.xml` declares `<groupId>com.digitalwallet</groupId>` and `<artifactId>backend</artifactId>`.
- [ ] `backend/pom.xml` declares `<quarkus.platform.version>3.33.2</quarkus.platform.version>` (3.33 LTS — confirmed 2026-05-25). The literal value is pinned — no `LATEST`, no version range.
- [ ] `backend/pom.xml` declares `jacoco-maven-plugin` pinned to a literal version (e.g. `<version>0.8.12</version>`) with a `check` execution scoped to `com/digitalwallet/*/service/**`, line coverage ≥ 0.80, `haltOnFailure=true`.
- [ ] [../../CLAUDE.md](../../CLAUDE.md) "Project Status" wording for the Quarkus version matches the version actually pinned in `pom.xml` (no stale "3.15.6" reference).
- [ ] `backend/pom.xml` declares the Testcontainers BOM + `postgresql` + `junit-jupiter` at test scope.
- [ ] `backend/pom.xml` declares `quarkus-hibernate-validator`, `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-micrometer-registry-prometheus`.
- [ ] `backend/src/main/java/org/acme/` does not exist; neither does `backend/src/test/java/org/acme/`.
- [ ] `backend/src/main/java/com/digitalwallet/package-info.java` exists with a single-line `package com.digitalwallet;` declaration.
- [ ] `backend/src/main/resources/application.properties` contains `quarkus.hibernate-orm.database.generation=none` (exact match).
- [ ] `backend/src/main/resources/application.properties` references `DB_URL`, `DB_USER`, `DB_PASSWORD` (canonical names per [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)) — not `DB_USERNAME` / `DB_HOST` / `DB_PORT` / `DB_NAME`.
- [ ] `backend/.env.example` mirrors the same env-var names.
- [ ] `.github/workflows/ci.yml` contains only the `backend` job (no `frontend`, no `docker-images`).
- [ ] `./mvnw -B verify` from `backend/` exits 0 on a clean checkout.
- [ ] `backend/target/site/jacoco/jacoco.xml` is produced after `verify`.
- [ ] `grep -R "org.acme" backend/` returns nothing.
- [ ] `grep -R "digital-wallet-api" .` returns nothing outside `.git/`.
- [ ] CI on the PR commit is green on the single `backend` job.

## 11. Security Considerations (MANDATORY)

Mapped against [../rules/security.md](../../.claude/rules/security.md).

- **§1 secrets and configuration** — no secrets enter the diff. `.env` (already gitignored) stays out of git. `.env.example` contains only env-var names + safe-by-default values (`DB_USER=digitalwallet`, never a real password). `application.properties` references env vars; no secret has a committed default. `JWT_PUBLIC_KEY` ships as a placeholder with an empty default (`${JWT_PUBLIC_KEY:}`) — the actual ES256 keypair generation is a Phase 1 deliverable. `JWT_PRIVATE_KEY` is **not** present in `application.properties` at all (it is resolved only by the JWT builder code Phase 1 introduces).
- **§2 authentication** — no endpoint exists yet, so JWT verification has nothing to protect. The `mp.jwt.verify.*` keys are present to fail closed (empty key → verification rejects everything) for any code that lands before Phase 1 finishes. ADR 0001 stays **Proposed**; it flips in Phase 1.
- **§3 authorization** — no endpoint exists; not applicable in this phase.
- **§4 input validation & injection** — `quarkus-hibernate-validator` is added now so Phase 1's first `@Valid` annotation has the runtime in place; no validators are written in this phase.
- **§5 transport & CORS** — no CORS config yet. Phase 1 owns the first allow-list. `quarkus.http.cors.*` is intentionally left unset (Quarkus default is deny by absence of header); adding it now would invite drift.
- **§7 sensitive data exposure** — no DTOs, no logs, no responses. N/A for this phase.
- **§8 rate limiting** — N/A; first rate limiter ships in Phase 6.
- **§10 secret scanning** — the diff is rename + dependency adds + property keys. Pre-commit `gitleaks` MUST pass — no real keys, no `password=`, no JWT material on disk.
- **§11 testing security-sensitive code** — no protected surface exists yet; no security tests written in this phase. Phase 1 introduces the first set.
- **§12 code-review checklist** — applicable items for this PR:
  - [ ] No secret material in the diff — confirmed (placeholders only).
  - [ ] No `console.log` / `System.out.println` — confirmed (no source code authored).
  - [ ] No new `VITE_*` env variable — N/A (no frontend touched).
  - [ ] Pre-commit `gitleaks` passes — verified locally before push.
  - [ ] Dependency scan clean of new High / Critical CVEs — verified via the GitHub Actions dependency scan that lights up automatically; manually `./mvnw dependency:tree | grep -i cve` is not required, but a `pnpm audit` on the now-absent frontend is N/A.

All other §12 items (RBAC, ownership checks, `@Valid`, idempotency, rate limit, WebSocket auth, replay tests, etc.) are N/A in Phase 0 because no endpoint exists.

## 12. Testing Strategy

Per [../rules/testing.md](../../.claude/rules/testing.md).

- **Unit tests:** none authored in this phase. The starter's two test classes are deleted in Step 2.
- **Integration tests:** none. The Testcontainers dependencies are wired in `pom.xml` so Phase 1's first integration test compiles immediately, but no containers are started in this phase.
- **NFR test contexts** ([../rules/testing.md §2.9](../../.claude/rules/testing.md#29-required-nfr-test-contexts)) — NFR1 / NFR2 / NFR3 / NFR7 / NFR8 are all N/A; the first NFR-bearing test lands in Phase 3.
- **Security tests** ([../rules/security.md §11](../../.claude/rules/security.md#11-testing-security-sensitive-code)) — N/A in Phase 0 (no protected surface).
- **Coverage floor:** JaCoCo wired with the gate scoped to `com/digitalwallet/*/service/**`. Zero classes match → vacuously ≥ 80 % → gate passes. Phase 1's first service class is the first time the floor has anything to measure; Phase 1 owns the first JaCoCo-gated test.
- **CI verification:** the single `backend` job runs `./mvnw -B verify`. The JaCoCo report artifact upload (already in `ci.yml` lines 43–49) remains in place — it produces a near-empty report in Phase 0 but the wiring is what we're testing.

The expected `./mvnw -B verify` output in Phase 0:

- Compile: 1 source file (`package-info.java`).
- Test phase: zero tests, zero failures (Surefire prints `No tests to run` — that is the expected state and is not a failure).
- Failsafe: zero `*IT` classes; no-op.
- JaCoCo `report` goal produces `target/site/jacoco/jacoco.xml`.
- JaCoCo `check` goal passes vacuously.
- Final BUILD SUCCESS.

## 13. Reference Files

Read in this order before implementing:

- [implementation-plan-mvp-master.md](implementation-plan-mvp-master.md) — Phase 0 row in §9 (Foundation).
- [../../CLAUDE.md](../../CLAUDE.md) — "Tech Stack (Mandated by Spec)" and "Module layout" sections; the LTS Quarkus version is committed here.
- [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering) — backend module tree.
- [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) — canonical env-var names.
- [../rules/backend_coding.md §13](../../.claude/rules/backend_coding.md#13-database-migrations) — Flyway forward-only / `database.generation=none`.
- [../rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines), [§3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code) — version baselines and idiom contract.
- [../rules/testing.md §1](../../.claude/rules/testing.md#1-coverage-targets), [§4.2](../../.claude/rules/testing.md#42-full-ci) — coverage floor, CI gate.
- [../rules/security.md §1](../../.claude/rules/security.md#1-secrets-and-configuration), [§10](../../.claude/rules/security.md#10-secret-scanning), [§12](../../.claude/rules/security.md#12-code-review-checklist--critical) — secrets policy.
- [../../docs/decisions/0007-build-tool.md](../../docs/decisions/0007-build-tool.md) — Maven (no Gradle substitution).
- [../../docs/decisions/0001-jwt-signing-algorithm.md](../../docs/decisions/0001-jwt-signing-algorithm.md) — still **Proposed**; Phase 1 flips it.

Existing files that will be modified or deleted (re-read each before editing):

- `digital-wallet-api/pom.xml`
- `digital-wallet-api/src/main/resources/application.properties`
- `digital-wallet-api/src/main/java/org/acme/GreetingResource.java`
- `digital-wallet-api/src/test/java/org/acme/GreetingResourceTest.java`
- `digital-wallet-api/src/test/java/org/acme/GreetingResourceIT.java`
- `digital-wallet-api/.env.example`
- `digital-wallet-api/README.md`
- `.github/workflows/ci.yml`

## 14. Risks & Dependencies

### Risks

- **`git mv` does not preserve blame cleanly when combined with a Maven `groupId`/`artifactId` change in the same commit.** Mitigation: do the rename in Step 1 as its own commit (or its own sub-step the squash will treat as a single rename), then bump `pom.xml` in Step 4 as a content change against the renamed path. Reviewers should verify `git log --follow backend/pom.xml` after the merge.
- **The Quarkus move (3.35 → latest LTS, e.g. 3.20.x) is a *minor downgrade* in version number but a move onto the supported LTS branch.** It is required because [../rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines) mandates Quarkus 3.x LTS and the 3.35 starter was never on an LTS branch. Mitigation: validate via `./mvnw clean verify` on a clean Maven local cache; the only concrete API surface change is in dependency tree shape (Hibernate ORM 6.x family), and we ship no business code yet — there is nothing to break.
- **JaCoCo `check` rule may trip on zero matched classes depending on plugin version.** Mitigation: pin `jacoco-maven-plugin` to a known-safe 0.8.x (≥ 0.8.11) and configure the `RULE` with `BUNDLE` scope so an empty inclusion matches and counts as zero/zero (passes). Verify locally in Step 8 — if the gate trips, switch the inclusion to `<excludes>` semantics, or guard the rule with `<element>CLASS</element>` + a permissive limit. The fallback is a single-line `<haltOnFailure>false</haltOnFailure>` flip with a TODO ticketed to flip back in Phase 1 — but that is the last resort, not the plan.
- **Quarkus dev-services may try to start Postgres in `dev` mode now that `quarkus-jdbc-postgresql` is on the classpath and `quarkus.datasource.devservices.enabled` is unset.** Mitigation: add `quarkus.datasource.devservices.enabled=false` to `application.properties` (test profile will get its own override from Testcontainers in Phase 1). This is part of Step 5.
- **CI YAML drift if reviewer hand-edits the workflow.** Mitigation: the `ci.yml` change is mechanical (delete two `job:` blocks); diff size makes review trivial.
- **Frontend job re-introduction in Phase F1 may diverge from the deleted version.** Mitigation: the original `frontend` and `docker-images` job bodies are preserved in commit `5759fbb`'s parent and retrievable from `git log -- .github/workflows/ci.yml`. Phase F1 / Phase 3 plans MUST cite the historical block when reintroducing it.

### Dependencies

- **External:** Maven 3.9.x via the wrapper; JDK 21 (Temurin) on CI (already configured in `ci.yml`). No Docker, no Testcontainers runtime in Phase 0 (test phase does not start containers because no test class uses `@Testcontainers`).
- **Internal cross-plan:** Phase 0 is a hard prerequisite for every subsequent phase. Phase 1 cannot start until `backend/` exists at the repo root and the LTS Quarkus / JaCoCo / Hibernate Validator / SmallRye JWT artifacts are on the classpath.
- **ADRs:** none move in Phase 0. ADR 0001 stays Proposed (Phase 1 flips it); ADR 0011 stays Proposed (Phase 8 flips it).
- **Tooling:** Pre-commit `gitleaks` hook should already be installed locally per [../rules/security.md §10](../../.claude/rules/security.md#10-secret-scanning); if not, the developer installs it before pushing the rename PR.

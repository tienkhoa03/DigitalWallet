# Upgrade policy

This file is the version-baseline contract for DigitalWallet. It governs which library versions new code targets, which idioms it MUST use, and what procedure to follow when a version needs to move.

> **Status:** the codebase is not yet scaffolded. Every rule cites either [../../project-info.md](../../project-info.md) or another rule file. Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

## 1. Supported baselines

The mandated stack lives in [../../project-info.md §4](../../project-info.md#4-tech-stack-mandated). The table below is the per-PR floor — code MUST target these versions or higher (within the same major where applicable).

| Component | Current target | Status | Source |
|---|---|---|---|
| Language (backend) | Java 21 (LTS) | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| Framework (backend) | Quarkus 3.x LTS | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| API library | RESTEasy Reactive (Quarkus default) | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| ORM | Hibernate ORM with Panache | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| Migrations | Flyway (versioned SQL only) | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend), [../../docs/database/migrations.md](../../docs/database/migrations.md) |
| Validation | Hibernate Validator (Bean Validation) | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| Build tool | Maven | Mandated — ADR #7 | [../../project-info.md §4.1](../../project-info.md#41-backend), [../../docs/decisions/0007-build-tool.md](../../docs/decisions/0007-build-tool.md) |
| Messaging client | SmallRye Reactive Messaging (Kafka extension) | Mandated | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| Resilience | SmallRye Fault Tolerance | Mandated for NFR8 | [../../project-info.md §4.1](../../project-info.md#41-backend) |
| Database | PostgreSQL 16 | Mandated | [../../project-info.md §4.3](../../project-info.md#43-persistence--data) |
| Cache / locks | Redis 7 | Mandated | [../../project-info.md §4.3](../../project-info.md#43-persistence--data) |
| Event backbone | Kafka | Mandated | [../../project-info.md §4.4](../../project-info.md#44-messaging--integration) |
| Frontend framework | Vue 3.x | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend), [../../docs/decisions/0008-frontend-stack.md](../../docs/decisions/0008-frontend-stack.md) |
| Frontend language | TypeScript 5.x strict | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Styling | Tailwind CSS 3.x | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Frontend state | Pinia (incl. TanStack Query / Vue Query) | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Frontend forms | VeeValidate + Zod | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Frontend package mgr | pnpm | Mandated — ADR #8 | [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Backend test framework | JUnit 5 + Mockito | Mandated | [../../project-info.md §4.5](../../project-info.md#45-testing--quality) |
| Backend integration test | Testcontainers (Postgres + Kafka + Redis) | Mandated | [../../project-info.md §4.5](../../project-info.md#45-testing--quality) |
| Backend coverage | JaCoCo (≥ 80 % service layer) | Mandated — NFR4 | [../../project-info.md §6 NFR4](../../project-info.md#6-non-functional-requirements--invariants), [testing.md §1](testing.md#1-coverage-targets) |
| Frontend test | Vitest + @testing-library/vue | Mandated | [../../project-info.md §4.5](../../project-info.md#45-testing--quality) |
| Frontend coverage | c8 (via Vitest) | Mandated | [../../project-info.md §4.5](../../project-info.md#45-testing--quality) |
| E2E | Playwright | Mandated (smoke per epic) | [../../project-info.md §4.5](../../project-info.md#45-testing--quality) |
| Runtime | Docker + Docker Compose | Mandated | [../../project-info.md §4.6](../../project-info.md#46-deployment) |
| CI | GitHub Actions | Mandated | [../../project-info.md §4.6](../../project-info.md#46-deployment) |
| Node | 20.x | Onboarding floor `(verify)` | [../../docs/onboarding/README.md](../../docs/onboarding/README.md) |

## 2. Migration posture

The project is migrating off its original organising principle and frontend framework. The rows below record the patterns being migrated away from; new code follows §3 and §4 directly.

| Legacy pattern | Reason it existed | New-code rule |
|---|---|---|
| Feature-based + layered backend (`api/` / `service/` / `persistence/` per module) | Original scaffolding before the architecture style was settled | Per-module hexagonal (ports & adapters): follow [backend_coding.md §1](backend_coding.md#1-project-structure) — dependencies point inward (`adapter` → `application` → `domain`); frameworks live only in `adapter/` packages. |
| React frontend stack (React 18 + Redux Toolkit + RTK Query + React Hook Form + React Router) | Original frontend scaffolding before the framework was settled | Vue 3 stack: follow [frontend_coding.md](frontend_coding.md) — Vue 3 SFCs (`<script setup>`), Pinia, TanStack Query (Vue Query), VeeValidate + Zod, Vue Router. |

When further legacy patterns surface (after the next major refactor or after merging an external contribution), append a new row here. Never overwrite a migration row silently.

## 3. Backend upgrade guardrails for new code

These idioms are the contract for every Java file landing in `backend/`. The `code-review` skill checks them.

- **Hexagonal layering (ports & adapters).** Each feature module is a self-contained hexagon: `domain/` and `application/` are framework-free; dependencies point INWARD only (`adapter` → `application` → `domain`). JPA `<Name>Entity` classes and `jakarta.persistence` annotations live ONLY in `adapter/out/persistence` — `domain/` model types carry no persistence annotations. Application services depend on outbound ports (`application/port/out`), never on concrete adapters. `@Transactional` is applied at the **application service (use-case impl) method**, not the inbound web adapter or the outbound persistence adapter ([backend_coding.md §1](backend_coding.md#1-project-structure), [backend_coding.md §3](backend_coding.md#3-service-layer)).
- **Records over Lombok.** Use Java `record` for DTOs and value objects. MUST NOT introduce Lombok in new code — records, `instanceof` patterns, and explicit constructors cover the same surface without bytecode magic.
- **Sealed interfaces** are preferred over open inheritance for closed type hierarchies (e.g. `DomainException` permits a finite set of subclasses). Use `sealed` + `permits` to make the closure explicit.
- **Pattern matching** (`switch` expressions, record patterns) over chained `if (x instanceof Y y)`.
- **Text blocks** (`"""..."""`) for multi-line SQL fragments in repositories. MUST NOT concatenate `+ "\n"` strings.
- **`var` for local variables** when the type is obvious from the right-hand side; explicit type when it adds clarity (especially for `BigDecimal`, `UUID`, `Instant`).
- **Virtual threads** (`Thread.ofVirtual()` / `@RunOnVirtualThread`) — the runtime is Java 21 specifically to make this available for Kafka consumers and HTTP threads ([../../project-info.md §4.1](../../project-info.md#41-backend)). Blocking JDBC and Kafka calls on virtual threads are the preferred posture once supported.
- **Quarkus extensions over raw libraries.** When the same capability is available as a Quarkus extension (Hibernate ORM Panache, SmallRye Reactive Messaging, SmallRye Fault Tolerance, SmallRye OpenAPI), use the extension. MUST NOT pull in a bare Hibernate, plain Kafka client, or Resilience4j dependency to bypass a Quarkus extension.
- **Package namespace policy:** all imports MUST be from the `jakarta.*` namespace (Jakarta EE 10+). MUST NOT introduce `javax.*` imports — Quarkus 3.x is on Jakarta.
- **Constructor injection only.** Field injection (`@Inject` on a field) is forbidden in new code ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide), [backend_coding.md §3](backend_coding.md#3-service-layer)).
- **`Instant` / `OffsetDateTime` over `Date` / `Calendar`** ([backend_coding.md §4](backend_coding.md#4-data-models--entities)).
- **`BigDecimal` over `double`/`float` for money** ([backend_coding.md §4](backend_coding.md#4-data-models--entities)).
- **`Optional` over `null` return** in repositories and services ([backend_coding.md §5](backend_coding.md#5-data-access)).
- **`Clock` injection** for time-aware logic; MUST NOT call `Instant.now()` directly inside service code that has time-dependent behaviour ([testing.md §2.2](testing.md#22-mocking-decision-matrix)).
- **No `synchronized` for cross-instance coordination.** Coordinate via Redis lock (NFR1) or DB row lock — JVM monitors do not work across replicas.

## 4. Frontend upgrade guardrails for new code

Stack mandated in [../../project-info.md §4.2](../../project-info.md#42-frontend) and [../../docs/decisions/0008-frontend-stack.md](../../docs/decisions/0008-frontend-stack.md).

- **TypeScript strict.** `tsconfig.json` runs with `strict: true`, `noUncheckedIndexedAccess: true`, `noImplicitOverride: true`. Disabling a flag locally is forbidden — fix the type instead. Type-check via `vue-tsc`.
- **Vue 3 idioms:**
  - Single-File Components with `<script setup lang="ts">`; the Options API is allowed but Composition API + `<script setup>` is the default for new code ([frontend_coding.md §1](frontend_coding.md#1-component-structure)).
  - `watch` / `watchEffect` with explicit sources over manual debouncing for non-urgent updates; `onMounted` / `onUnmounted` for lifecycle.
  - Idempotent effects — setup MUST tolerate Vue dev remount: clean up every subscription / timer in `onUnmounted` (or the watch stop handle).
- **Pinia idioms:**
  - `defineStore` (setup-style stores) over hand-written stores.
  - Default to TanStack Query (Vue Query) for server cache; reach for a Pinia store action only when the server-cache client is not a fit ([frontend_coding.md §2](frontend_coding.md#2-state-management)).
  - `computed` / Pinia getter for derived data — inline derivations that allocate new objects every render are a defect.
- **VeeValidate + Zod:**
  - `toTypedSchema` (`@vee-validate/zod`) is the validation bridge — MUST NOT hand-roll a separate validator.
  - One Zod schema per form lives in `<feature>/<form>.schema.ts` ([frontend_coding.md §4](frontend_coding.md#4-forms--validation)).
- **Router idioms:**
  - Vue Router v4; functional guards — a global `router.beforeEach` reading `to.meta` plus guard wrapper components (`<RequireAuth>`, `<RequireRole>`); MUST NOT imperatively `router.push` redirect inside page component bodies ([frontend_coding.md §5](frontend_coding.md#5-routing--route-protection)).
- **WebSocket idioms:** one shared client per session ([frontend_coding.md §13](frontend_coding.md#13-domain-specific-conventions)). MUST NOT spawn a `new WebSocket(...)` per component.
- **Module resolution:** absolute imports via `@/` alias; relative imports for same-folder siblings only ([frontend_coding.md §7](frontend_coding.md#7-import-ordering)).
- **Package manager:** pnpm. MUST NOT commit `package-lock.json` or `yarn.lock` — only `pnpm-lock.yaml` ([../../docs/decisions/0008-frontend-stack.md](../../docs/decisions/0008-frontend-stack.md)).
- **No introducing competing libraries** for state, forms, styling, network, or testing. The mandated stack is the answer; new dependencies in those categories require an ADR per §5.

## 5. When to break this policy

A version baseline or library mandate MAY only be broken under one of two conditions:

1. **Critical CVE** in the current target with no patched version on the supported line. The fix-forward path is to bump to the lowest patched version (preserving major / framework family). Document the CVE id and the resolved version in [../../docs/decisions/](../../docs/decisions/) when the bump crosses a minor.
2. **New feature requires a higher version.** Document the feature, the minimum version that supports it, and the migration impact in an ADR before merging the dependency change.

**Procedure:**

1. Draft an ADR using [../../docs/decisions/template.md](../../docs/decisions/template.md).
2. Set **Status** to **Proposed** in the same PR that proposes the upgrade.
3. List the alternatives considered (stay on current version + workaround vs. bump).
4. On merge: change **Status** to **Accepted**, update the row in §1 of this file, and update the relevant rule in [backend_coding.md](backend_coding.md) / [frontend_coding.md](frontend_coding.md) if the idiom moves.
5. If the upgrade supersedes a previous ADR, add `Supersedes:` / `Superseded by:` markers per [../../docs/decisions/README.md](../../docs/decisions/README.md).

A dependency-version bump that does **not** change a baseline above (e.g. patch upgrade of a transitive library) does not need an ADR — it belongs in the PR description.

## 6. Accepted risks

When a version cannot be moved (legacy CVE inherited from a transitive dependency, deferred upgrade pending a vendor release, etc.), the risk MUST be accepted explicitly. ADRs live under [../../docs/decisions/](../../docs/decisions/).

Required fields for an "accepted risk" ADR:

- **Title** — `00NN-accept-<short-slug>.md`.
- **Status** — `Accepted` with the date.
- **Context** — what the risk is, which CVE / version is the concern, which component carries it.
- **Decision** — explicitly: "we accept this risk for now".
- **Mitigations in place** — compensating controls (e.g. WAF rule, input validation, network isolation).
- **Revisit if** — the trigger that ends acceptance (vendor release date, upstream patch, regulatory deadline).

An accepted-risk ADR with no **Revisit if** field is a defect — risks without an off-ramp accumulate silently. The ADR list lives in [../../docs/decisions/README.md](../../docs/decisions/README.md#index).

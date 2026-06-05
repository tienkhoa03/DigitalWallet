# Security rules

This file is the cross-cutting security floor for every part of DigitalWallet. The `code-review` skill checks pull requests against §12 explicitly.

> **Status:** the codebase is not yet scaffolded. Every rule cites either `docs/` or a section of [../../project-info.md](../../project-info.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

Source baselines: [../../project-info.md §8](../../project-info.md#8-security-baseline), [../../docs/business-rules/README.md](../../docs/business-rules/README.md) Cross-cutting security rules.

## 1. Secrets and configuration

- **No committed secrets.** API keys, JWT private keys, DB passwords, LLM keys MUST NOT appear in the repository. Source: [../../project-info.md §8](../../project-info.md#8-security-baseline).
- **Env-var loading:** 12-factor configuration in MVP, sourced from process environment. Defaults for non-secret values live in `application.properties` ([../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)); secret values MUST resolve to env vars with no committed default. Production secret manager is deferred ([../../project-info.md §8](../../project-info.md#8-security-baseline)).
- **Frontend env rules:** any variable readable in the browser (`VITE_*` per Vite convention) is **public**. MUST NOT prefix a secret with `VITE_`. The LLM API key, JWT signing key, and DB credentials MUST NEVER leave the backend.
- **Log scrubbing:** the backend logger MUST NOT emit secrets, JWT bearer tokens, full `Idempotency-Key` values, full email addresses, full names, balances, or LLM prompt / response bodies. See §7 and [backend_coding.md §11](backend_coding.md#11-logging). The frontend logger (shared error reporter) follows the same rule.
- **Secrets in error responses:** stack traces and configuration values MUST NOT appear in user-facing error bodies (see §7).

## 2. Authentication

Auth scheme is committed in [../../project-info.md §8](../../project-info.md#8-security-baseline) (stateless JWT, ES256). The algorithm-level ADR is [../../docs/decisions/0001-jwt-signing-algorithm.md](../../docs/decisions/0001-jwt-signing-algorithm.md) — currently **Proposed**, so the operational specifics below are the floor that holds once it is **Accepted**.

- **Algorithm:** ES256 (ECDSA P-256). Tokens MUST be verified with the `ES256` algorithm and the configured public key. MUST NOT accept `alg: none`, `HS256`, or any algorithm not on a hard-coded allow-list — the canonical JWT-confusion attacks rely on lax allow-lists.
- **Clock skew:** verification MUST allow ≤ 30 seconds of clock skew on `nbf` / `exp`. Tokens beyond skew are rejected.
- **Verification placement:** the JWT MUST be verified before the JAX-RS request reaches the service layer. WebSocket upgrades MUST validate the JWT before accepting the connection ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) RBAC).
- **Password hashing:** user passwords MUST be hashed with a memory-hard algorithm (Argon2id preferred; bcrypt acceptable with cost ≥ 12). Plain SHA-256 / MD5 / SHA-1 are forbidden. The persisted column is `account.password_hash` ([../../docs/database/README.md](../../docs/database/README.md) `account`).
- **Account recovery:** MUST be a separate authenticated flow that issues a single-use, time-bounded token; recovery tokens MUST NOT extend or re-issue an existing JWT.
- **Enumeration prevention:** sign-in failures MUST return the same `errorKey: "auth.invalid_credentials"` regardless of whether the email exists. The same applies to password-reset acknowledgement.
- **Audit trail — DEFERRED in MVP:** the `audit_log` table is deferred (see [../../project-info.md §8](../../project-info.md#8-security-baseline), [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md)). Authentication-event auditing, fraud-block auditing, and suspension/un-suspension auditing return when manual unsuspend / role-grants UI / admin PII reads ship — at which point the table and the corresponding "MUST write to `audit_log`" rules return via an ADR superseding [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).
- **`auth.rate_limited`:** repeated failed sign-ins MUST trigger the rate-limit envelope (§8) — no silent exponential lockout.

## 3. Authorization

- **Default-deny:** every endpoint and every service entrypoint MUST require an authenticated principal except the explicit public list (`POST /accounts`, `POST /auth/login`). New endpoints default to authenticated. Controller-level `@RolesAllowed` is the first line; the service layer re-checks the role and ownership ([../../project-info.md §8](../../project-info.md#8-security-baseline), [backend_coding.md §3](backend_coding.md#3-service-layer)).
- **Ownership checks:** any handler that takes a path parameter identifying user-owned data (`{walletId}`, `{accountId}`, `{budgetId}`) MUST verify that the authenticated principal owns the resource — even when the role is correct. Role gives capability; ownership gives access.
- **Admin reads of user data:** *(MVP defers the `audit_log` row — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)* When the table returns, admin reads MUST log to `audit_log` with `action = "admin.user.read"` and the subject `account_id`.
- **Role separation — DEFERRED:** the dedicated `FRAUD_ANALYST` role is **deferred in MVP**; only `USER` and `ADMIN` ship ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system), [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md)). Endpoints MUST NOT promote one role's permissions to the other transitively. When `FRAUD_ANALYST` returns, the least-privilege separation between admin and analyst returns with it.
- **Role escalation prevention:** role grants are not user-mutable in MVP — every user holds the default `USER` role and admin promotion happens out-of-band. When an admin role-management path ships, the handler MUST refuse to elevate the caller's own user row.
- **Cross-user leakage:** WebSocket fan-out for user notifications MUST scope by `account_id` server-side. Client-side filtering is NEVER acceptable ([../../docs/business-rules/pfm-notifications-rules.md](../../docs/business-rules/pfm-notifications-rules.md) Cross-cutting).

## 4. Input validation & injection

- **Boundary validation:** every JAX-RS resource that accepts a body MUST validate it with Bean Validation (`@Valid`). See [backend_coding.md §12](backend_coding.md#12-validation). Hibernate Validator failures map to `errorKey: "validation.invalid_payload"` ([backend_coding.md §8](backend_coding.md#8-exception-handling)).
- **SQL / JPQL injection:** queries MUST use bound parameters. MUST NOT concatenate user-supplied strings into JPQL / SQL fragments. The most common offender is a sort parameter — see [backend_coding.md §10](backend_coding.md#10-pagination--sort-safety).
- **Sort whitelist:** every sort parameter MUST be mapped through an explicit whitelist before reaching the query layer. Unknown keys raise `validation.invalid_payload`.
- **File upload:** out of scope in MVP (no endpoint accepts files). If a future endpoint adds uploads, it MUST validate content-type against a whitelist, cap size, and store outside the document root.
- **XSS:** all user-supplied strings rendered in the React app MUST be rendered as text (default React behaviour). MUST NOT pass user input to `dangerouslySetInnerHTML`. The shared error reporter sanitises message strings before rendering.
- **Open-redirect:** if a future endpoint accepts a redirect URL, it MUST validate against an allow-list of relative paths or host-equality. Never `Location` -> arbitrary attacker-controlled URL.
- **LLM prompt injection mitigation:** prompts sent to the LLM MUST be anonymised — only aggregated amounts and category labels ([../../project-info.md §8](../../project-info.md#8-security-baseline), [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) Cross-cutting). Free-form user fields that could carry instructions to the model MUST be stripped or escaped before inclusion.

## 5. Transport & CORS

- **HTTPS-only:** production traffic MUST run over HTTPS. HTTP is permitted only inside the local `docker-compose` network ([../../project-info.md §8](../../project-info.md#8-security-baseline)).
- **CORS allow-list:** the backend MUST be configured with an explicit list of allowed origins per profile. `*` origin is forbidden in `prod`.
- **Security headers** the backend MUST set on every response:

  | Header | Value | Notes |
  |---|---|---|
  | `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | `prod` only. |
  | `Content-Security-Policy` | starts with `default-src 'self'`; tightened per route | Inline scripts are forbidden in production builds. |
  | `X-Frame-Options` | `DENY` | No third-party embedding. |
  | `X-Content-Type-Options` | `nosniff` | — |
  | `Referrer-Policy` | `strict-origin-when-cross-origin` | — |
  | `Permissions-Policy` | minimal — disable geolocation, microphone, camera, payment by default | — |

- **CSRF:** auth is bearer-token in the `Authorization` header (no cookie auth). CSRF tokens are NOT required for the JWT path. If a future endpoint accepts cookie auth, CSRF MUST be added (double-submit cookie or `SameSite=strict`).
- **WebSocket origin check:** the WebSocket upgrade handler MUST validate the `Origin` header against the same allow-list as CORS.

## 6. Sessions & token handling (frontend)

Storage trade-offs `<!-- not-yet-adopted -->` — the default below is the rule until a route is added that explicitly needs another mode:

| Mechanism | XSS risk | CSRF risk | UX | Decision |
|---|---|---|---|---|
| `localStorage` | High — any XSS reads it | None (header-injection) | Survives reload | Acceptable default given bearer-token + strict CSP. |
| `sessionStorage` | High | None | Lost on tab close | OK for short-lived demo flows. |
| `HttpOnly` cookie | None | High — needs CSRF | Survives reload | Only when cookie auth is adopted; CSRF must be added. |
| In-memory only | None | None | Lost on reload | Best for kiosk / shared device — too aggressive as default. |

- **Default:** the JWT is held in memory (Redux store) for the lifetime of the tab; `localStorage` is used only to remember "I was logged in" via an opaque flag, NOT the token itself.
- **Header injection:** the RTK Query `baseQuery` MUST inject `Authorization: Bearer <token>` from the in-memory store. MUST NOT read the token from `document.cookie`.
- **Expiry & logout:** on `401`, the `baseQuery` MUST dispatch a logout action that clears the token from memory and redirects to `/login` ([frontend_coding.md §3](frontend_coding.md#3-api-calls)). MUST NOT silently retry.
- **UX vs security:** a "Remember me" flow MUST NOT extend the JWT lifetime by storing the refresh token in `localStorage`. If refresh-token UX is added later, the refresh token MUST be `HttpOnly` cookie with CSRF protection.

## 7. Sensitive data exposure

- **DTO constraints:** response DTOs MUST NOT include `password_hash`, JWT signing secrets, internal IDs that leak counts, or full PII unless explicitly required by the endpoint. See [backend_coding.md §6](backend_coding.md#6-dtos).
- **User-facing error messages:** the `message` field of the error envelope is informational and human-readable ([../../docs/api/README.md](../../docs/api/README.md#error-response-shape)). MUST NOT include stack traces, SQL fragments, internal IDs, or configuration values. Clients branch on the stable `error_key`, not the message.
- **Idempotency-Key handling in logs:** the full key is sensitive — a leaked key allows replay of a mutating request. Logs MUST emit either a salted hash or the first 8 characters only ([backend_coding.md §11](backend_coding.md#11-logging)).
- **LLM payloads:** prompts and responses MUST NOT be persisted or logged in any form that contains user identifiers. Anonymised aggregates only ([../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) Cross-cutting on anonymisation).
- **PII columns:** the columns marked PII in [../../docs/database/README.md](../../docs/database/README.md) (`account.email`) MUST be considered sensitive in every projection.

## 8. Rate limiting & abuse

Source: [../../project-info.md §8](../../project-info.md#8-security-baseline), [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles).

| Endpoint | Limit | Mechanism | Env vars |
|---|---|---|---|
| `POST /transfers` | 10 per minute per account | Redis token bucket in `shared/` rate-limit middleware | `RATELIMIT_TRANSFER_PER_MINUTE` (default 10) |
| `POST /advisor/*` | 5 per hour per account | Redis token bucket in `shared/` rate-limit middleware | `RATELIMIT_ADVISOR_PER_HOUR` (default 5) |
| `POST /auth/login` | Progressive back-off on repeated failure `<!-- not-yet-adopted -->` | Redis counter keyed on (user, IP) | — |

- **Failure mode:** exceeded limit returns HTTP 429 with `errorKey: "ratelimit.exceeded"` and a `Retry-After` header ([backend_coding.md §8](backend_coding.md#8-exception-handling)).
- **Distinction from fraud rules:** velocity (FR2.1), volume (FR2.2), and the `account.fraud_status` check (FR2.4) are **also** preventive — they reject the offending transaction inline on the synchronous money path per NFR9 ([../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants), [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md)). The two systems MUST NOT be conflated, however: rate limits return `ratelimit.exceeded` (HTTP 429) and signal abuse of the endpoint; fraud blocks return `fraud.velocity_exceeded` / `fraud.volume_exceeded` (HTTP 422) or `account.suspended` (HTTP 403 — error key preserved for API back-compat) and signal user-level risk. Each has its own counter and its own envelope. Cross-event fraud analysis, alerting (FR2.5), and the suspension-policy decision (FR2.4) remain on the Kafka consumer.
- **Progressive back-off:** repeated 429s for the same user SHOULD back off in client retry policy, but the server does not increase the limit window. Server policy is fixed-window token bucket per the env vars.

## 9. Dependencies

- **Scanning cadence:** dependency vulnerabilities MUST be scanned on every PR build (GitHub Actions). Tool choice `<!-- not-yet-adopted -->`; `mvn dependency:tree` + OWASP Dependency-Check on the backend and `pnpm audit` on the frontend are the baseline.
- **Severity policy:**

  | CVSS | Action |
  |---|---|
  | Critical (9.0+) | Block merge until upgraded or mitigated; document mitigation in [../../docs/decisions/](../../docs/decisions/). |
  | High (7.0–8.9) | Fix within 7 days. |
  | Medium (4.0–6.9) | Fix within 30 days or document acceptance per §6 of [upgrade-policy.md](upgrade-policy.md). |
  | Low (< 4.0) | Best-effort; bundle with the next scheduled bump. |

- **Legacy CVEs:** any inherited CVE older than the last release MUST be either fixed forward or accepted via the ADR procedure in [upgrade-policy.md §5](upgrade-policy.md#5-when-to-break-this-policy).

## 10. Secret scanning

- **Pre-commit hook:** `gitleaks` MUST run as a pre-commit hook ([../../project-info.md §8](../../project-info.md#8-security-baseline)). The hook is wired by the developer onboarding step `<!-- not-yet-adopted -->`.
- **CI scan:** `gitleaks` MUST also run as a GitHub Actions step on every PR. A finding blocks merge.
- **Rotation protocol:** when a secret is found to have been committed:
  1. Rotate the secret immediately at the source (cloud provider, LLM vendor, JWT keypair).
  2. Replace it in env var configuration.
  3. Open a security note under [../../docs/decisions/](../../docs/decisions/) recording the rotation date.
  4. Git history MUST NOT be rewritten on `main` to remove the leak — the leak is already public; rewriting hides the audit trail and breaks every other clone.

## 11. Testing security-sensitive code

See [testing.md](testing.md) for the testing contract. The required security test types are:

| Test type | What it asserts | Where |
|---|---|---|
| Unauthenticated request | Returns 401 with `auth.invalid_credentials` (login) or 403 / 401 (protected endpoints). | Per endpoint, in resource integration tests. |
| Wrong-role request | Returns 403 with `auth.forbidden`. | Per endpoint, in resource integration tests. |
| Wrong-tenant request | `USER` A cannot read or mutate `USER` B's wallet, budget, or notifications. | Per ownership-bound endpoint. |
| Replay | Same `Idempotency-Key` with the same body returns the original outcome; same key with a different body returns `idempotency.replay_conflict`. | Wallet service integration tests (NFR3). |
| Boundary | `threshold_percent` outside `[1, 100]`, `amount <= 0`, malformed currency code → `validation.*`. | DTO validation tests. |
| XSS payload | A free-form string containing `<script>` is rendered as text in the frontend, not executed. | Vitest + React Testing Library. |
| Rate limit | The 11th transfer per minute returns 429 with `Retry-After`; the 6th advisor call per hour returns 429. | Rate-limit middleware tests. |
| Outbox event on block | A fraud-driven block (FR2.1–FR2.2) writes the expected `transaction.blocked` outbox row (no ledger row). | Service integration tests. *(MVP defers the `audit_log` row originally specified here — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)* |

## 12. Code-review checklist — CRITICAL

The `code-review` skill checks every PR against this checklist. Each item is a release blocker.

- [ ] No secret material in the diff (keys, passwords, tokens, full JWT, full Idempotency-Key) — §1, §10.
- [ ] No `console.log` / unstructured `System.out.println` left in production code — §1, [backend_coding.md §11](backend_coding.md#11-logging), [frontend_coding.md §18](frontend_coding.md#18-bundle-hygiene).
- [ ] Every new mutating money endpoint requires `Idempotency-Key` — §11 (replay), [backend_coding.md §2](backend_coding.md#2-routing--controllers).
- [ ] Every new endpoint enforces RBAC at **both** the controller and the service layer — §3.
- [ ] Every new endpoint that takes an owner-scoped path parameter performs an ownership check — §3.
- [ ] Every new endpoint that accepts a body uses `@Valid` and a Zod schema (frontend) — §4, [backend_coding.md §12](backend_coding.md#12-validation), [frontend_coding.md §4](frontend_coding.md#4-forms--validation).
- [ ] Every new sort / filter parameter is whitelisted server-side — §4, [backend_coding.md §10](backend_coding.md#10-pagination--sort-safety).
- [ ] Every new SQL / JPQL query uses bound parameters — §4.
- [ ] Every new response DTO omits `password_hash`, JWT secrets, internal counters, raw stack traces — §7.
- [ ] Every new error path returns a typed `errorKey` matching [../../docs/api/README.md](../../docs/api/README.md#error-response-shape) — [backend_coding.md §8](backend_coding.md#8-exception-handling).
- [ ] No `dangerouslySetInnerHTML` on user input — §4, [frontend_coding.md §19](frontend_coding.md#19-anti-patterns).
- [ ] No new `VITE_*` env variable holds a secret — §1.
- [ ] LLM prompts contain only aggregated amounts and category labels — no user identifiers — §4, [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md).
- [ ] *(MVP defers `audit_log`. Item returns with the table — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)* Audit-log row written for any new admin or money-mutation action — §3, §7.
- [ ] Pre-commit hook (gitleaks) passes — §10.
- [ ] Rate-limit middleware applied to any new `POST /transfers`-class or `POST /advisor/*`-class endpoint — §8.
- [ ] WebSocket upgrades validate the JWT and the `Origin` header — §2, §5.
- [ ] Auth tests (unauthenticated / wrong-role / wrong-tenant) added for every new protected endpoint — §11.
- [ ] Idempotency replay test added for every new mutating money endpoint — §11.
- [ ] Dependency scan clean of new High / Critical CVEs — §9.

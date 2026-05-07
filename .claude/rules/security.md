# Security — Digital Wallet

*Cross-cutting security rules for backend and frontend. Every "MUST" is a release blocker. Every "MUST NOT" describes a defect that fails review.*

> See also: [docs/architecture/README.md §6](../../docs/architecture/README.md), [docs/decisions/](../../docs/decisions/).
>
> **Status:** the codebase is not yet scaffolded. Rules cite [docs/](../../docs/) and the spec; runtime enforcement (filters, interceptors, scanners) is `<!-- not-yet-adopted -->` until the corresponding modules land.

## 1. Secrets and configuration

### 1.1 No committed secrets

- `.env`, `*.pem`, `*.key`, `id_rsa*`, `*.p12`, `*.pfx`, `credentials.json` MUST be in `.gitignore` before any code lands that might reference them.
- A pre-commit hook runs `gitleaks` (or equivalent) on every commit. `<!-- not-yet-adopted -->`
- A discovered committed secret is treated as a CVE: rotate first, force-push later — the rotation always happens, the rewrite is optional.

### 1.2 Env-var loading

| Layer | Mechanism |
|---|---|
| Backend | Microprofile Config (`@ConfigProperty`) reading from env first, then `application.properties` defaults |
| Frontend | `environment.<profile>.ts` files; never inject server-side secrets here |

Env-var names follow the table in [docs/architecture/README.md §7](../../docs/architecture/README.md).

### 1.3 Frontend vs. backend env rules

- **Anything in `environment.*.ts` ships to the browser.** Never put a server-only secret, a server URL with credentials, or a third-party API key with write privileges in those files.
- API base URL, public OAuth client IDs, feature flags, telemetry endpoint URLs — those are appropriate for `environment.*.ts`.

### 1.4 Log scrubbing

Logs MUST NOT contain: passwords, password hashes, full tokens, full `Idempotency-Key` values, full PII (email, full name, full account numbers). See [backend_coding.md §11.4](backend_coding.md) for the backend log policy.

---

## 2. Authentication (Backend)

> The auth scheme is unspecified by [the spec](../../docs/architecture/README.md#6-auth-flow). Until an ADR commits to one, the rules below apply once an auth path is added.

### 2.1 Token issuance (when JWT is chosen)

- Algorithm: `ES256` or `RS256`. **Never** `HS256` with a shared secret in a browser-facing system. **Never** `none`.
- Key size: ≥ 2048 bits for RSA, P-256 for EC.
- Claims: `sub` (account id), `iat`, `exp`, `nbf`, `iss`, `aud`. No PII in custom claims.
- Lifetime: ≤ 15 minutes for access tokens. Refresh handled separately.

### 2.2 Clock skew

Verifiers tolerate ≤ 60 seconds of skew. Anything more is a misconfigured clock — fix the clock, do not widen the tolerance.

### 2.3 Verification

Every protected endpoint MUST verify signature, expiration, and audience before any business logic runs. A verification failure produces `401` with no detail.

### 2.4 Password hashing

If passwords are introduced: `argon2id` with parameters tuned for ~250 ms per hash on production hardware. **Never** plain SHA-2, **never** unsalted, **never** bcrypt with cost < 12. `<!-- not-yet-adopted -->`

### 2.5 Account recovery

- Recovery tokens are single-use, ≤ 30-minute expiry, stored as a hash (not the raw token).
- Recovery requests rate-limited per account and per IP — see §8.

### 2.6 Account enumeration prevention

Login and recovery endpoints return identical responses for "user not found" and "wrong password / wrong email". Response time MUST be constant within ±50 ms — pad with a dummy hash compute when the user does not exist.

---

## 3. Authorization (Backend)

### 3.1 Default-deny posture

Every JAX-RS resource without `@RolesAllowed` or an explicit `@PermitAll` MUST be unreachable. A "default-allow if no annotation" filter is forbidden. See [backend_coding.md §9.3](backend_coding.md).

### 3.2 Ownership / tenant checks

Every endpoint that operates on a wallet MUST verify the caller owns it (or has admin role) **before** the balance read.

```java
if (!wallet.getAccountId().equals(currentAccountId) && !isAdmin()) {
    throw new ForbiddenException();
}
```

A path-param ID is not authorization — it is a request, and the server proves the requester is allowed.

### 3.3 Role escalation prevention

Role transitions go through a dedicated admin endpoint, never as a side effect of a profile update. The role field MUST NOT be writable from a self-service `PATCH /accounts/me`.

---

## 4. Input validation & injection

### 4.1 Boundary validation

Every JAX-RS resource validates its input via Bean Validation (`@Valid`). See [backend_coding.md §12](backend_coding.md). Service-layer validation is in addition to, not instead of, the boundary check.

### 4.2 SQL / JPQL injection

- All queries use parameter binding.
- String concatenation of user input into JPQL or native SQL is a release blocker.
- Sort fields use the whitelist in [backend_coding.md §10.2](backend_coding.md).

### 4.3 File upload

`<!-- not-yet-adopted -->` No file-upload endpoint exists. When introduced:

- MIME sniffed server-side (Apache Tika or equivalent), not trusted from `Content-Type`.
- Max size enforced before parsing.
- Office documents: macros stripped or rejected.
- Files written to a sandboxed bucket, never to the app's working directory.

### 4.4 XSS

- Frontend: never `bypassSecurityTrust*` without an ADR.
- Server-rendered messages are plain text — never HTML. `application/json` responses are escaped on render in the browser.
- Error messages returned in API responses are escaped at render time on the client; do not interpolate them as HTML.

### 4.5 Open-redirect

Any endpoint that takes a redirect target validates against a whitelist of relative paths. External URLs are rejected with `400`.

---

## 5. Transport & CORS

### 5.1 HTTPS

Production traffic is HTTPS-only. HTTP requests on the app port redirect to HTTPS. HSTS preload after the first month of stable HTTPS.

### 5.2 CORS

`Access-Control-Allow-Origin: *` in production is a **release blocker**. Origins are an explicit list, sourced from config. Wildcard is acceptable in the `local` profile only.

### 5.3 Security headers

| Header | Value | Notes |
|---|---|---|
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` | After HTTPS is stable |
| `Content-Security-Policy` | `default-src 'self'; …` | Tighten per environment; no `unsafe-inline` for scripts |
| `X-Frame-Options` | `DENY` | Plus CSP `frame-ancestors 'none'` |
| `X-Content-Type-Options` | `nosniff` | Always |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Always |
| `Permissions-Policy` | feature-allow list scoped to needed APIs | |

### 5.4 CSRF

For session-cookie auth: every state-changing endpoint requires a double-submit token (header value matches cookie value).

For pure-Bearer-token auth (no cookies), CSRF is structurally absent — but cookies must then truly be absent, including legacy session cookies left over from earlier deployments.

---

## 6. Sessions & token handling (Frontend)

### 6.1 Storage trade-offs

| Mechanism | XSS exposure | CSRF exposure | Verdict |
|---|---|---|---|
| `localStorage` | high (any script reads it) | none | **Forbidden** for auth tokens |
| `sessionStorage` | high | none | **Forbidden** for auth tokens |
| `HttpOnly` + `Secure` + `SameSite=Strict` cookie | none | mitigated by SameSite + CSRF token | **Default** |
| In-memory (lost on reload) | low (still XSS-able) | none | Acceptable for short sessions when paired with a refresh cookie |

### 6.2 Header injection

The auth header is added by a single `HttpInterceptorFn` reading the in-memory token. Components MUST NOT add auth headers manually — that pattern leaks tokens into logs and breaks rotation.

### 6.3 Expiry & logout

- 401 from any backend endpoint → interceptor clears auth state and routes to login.
- Explicit logout calls a server endpoint that revokes the refresh token, then clears local state.

### 6.4 UX vs. security

A "remember me" checkbox extends refresh-token lifetime — never the access-token lifetime, never the storage location.

---

## 7. Sensitive data exposure

### 7.1 DTO constraints

Response DTOs MUST NOT contain: password hashes, internal IDs that leak structure (e.g., sequential integers), full account numbers (mask all but last 4), other users' email addresses.

### 7.2 User-facing error messages

User-facing messages MUST NOT include: stack traces, SQL fragments, JPA exception class names, internal hostnames, file paths, raw third-party error bodies. The error response shape is documented in [backend_coding.md §8.4](backend_coding.md).

### 7.3 Idempotency keys

`Idempotency-Key` values are treated as request secrets in logs and dashboards: log presence + last 4 chars only. Never echo the full key in a response body.

---

## 8. Rate limiting & abuse

### 8.1 Required throttling

| Endpoint | Limit | Key |
|---|---|---|
| `POST /transfers` | 10 / minute / account; 60 / minute / IP | account id + IP |
| `POST /accounts` (signup) | 5 / hour / IP | IP |
| `POST /accounts/login` | 5 / minute / account; 20 / minute / IP | account id + IP |
| Recovery flows | 3 / hour / account | account id |

`<!-- not-yet-adopted -->` Implementation is via Redis (already in the stack — see [docs/architecture/README.md §2](../../docs/architecture/README.md)).

### 8.2 Progressive back-off

Repeated failures from the same key trigger exponential back-off: 1 s, 2 s, 4 s, capped at 60 s. Locked accounts are released by an admin or on a successful recovery flow.

### 8.3 Distinction from fraud rules

Rate limiting is a **request-path** control (rejects requests). Fraud rules ([fraud-rules.md](../../docs/business-rules/fraud-rules.md)) are an **observation** control (raises alerts, does not reject). Do not conflate them.

---

## 9. Dependencies

### 9.1 Scanning cadence

CI runs a vulnerability scanner (Snyk / OWASP Dependency-Check / Trivy) on every PR and nightly. `<!-- not-yet-adopted -->`

### 9.2 Severity policy

| Severity | Policy |
|---|---|
| Critical | Block merge; patch within 24 h |
| High | Block merge; patch within 7 days |
| Medium | Tracked, patched in the next release window |
| Low | Backlog |

### 9.3 Legacy CVEs

Tracked in [docs/decisions/](../../docs/decisions/) with a "new-code rule" — see [upgrade-policy.md §2](upgrade-policy.md).

---

## 10. Secret scanning

### 10.1 Pre-commit

Developers install the team-provided pre-commit hook (`gitleaks` or equivalent). `<!-- not-yet-adopted -->`

### 10.2 Rotation protocol

- Detection → revoke the credential within 1 hour.
- Audit logs scanned for the credential's use during its exposure window.
- Replacement credential issued and deployed before announcing rotation complete.
- The discovery is recorded in an ADR or accepted-risks log (see [upgrade-policy.md §6](upgrade-policy.md)).

---

## 11. Testing security-sensitive code

> See also: [testing.md](testing.md).

### 11.1 Required test types

- **Unauthenticated** — every protected endpoint has a test that calls it without auth and asserts `401`.
- **Wrong-tenant** — every ownership-checking endpoint has a test that calls it as a different account and asserts `403`.
- **Idempotency replay** — `POST /transfers` test sends the same key twice and asserts a single balance change.
- **Boundary** — pagination size, sort whitelist, max body size.
- **Payload** — XSS strings (`<script>alert(1)</script>`, `'><img src=x onerror=…>`) in user-controlled fields, asserting they survive a round trip without HTML escaping changing meaning on the way out.

---

## 12. Code review checklist

Reviewers tick every box that applies to the diff. An unchecked box that should be checked blocks merge.

- [ ] No new secrets in committed files (`.env`, `*.pem`, configs with credentials).
- [ ] Every new endpoint has `@RolesAllowed` or an explicit `@PermitAll`.
- [ ] Every new endpoint that operates on user-owned data has an ownership check before any DB read of that resource.
- [ ] All input is validated at the JAX-RS boundary via `@Valid`.
- [ ] No string concatenation into JPQL or native SQL.
- [ ] Sort and pagination use the whitelists from [backend_coding.md §10](backend_coding.md).
- [ ] No `console.log`, no `System.out.println`, no `printStackTrace()`.
- [ ] No password / token / full `Idempotency-Key` value in logs.
- [ ] CORS origins are explicit (no `*` outside the `local` profile).
- [ ] Error responses use the canonical shape and an enumerated `errorKey`.
- [ ] Frontend reads/writes auth tokens only through `core/auth/`.
- [ ] No new `localStorage` / `sessionStorage` key for auth.
- [ ] If a new dependency is added, the scanner output is clean.
- [ ] Tests include unauthenticated, wrong-tenant, and replay/idempotency cases where applicable.
- [ ] If this change touches a rate-limited endpoint, the limit table in §8.1 is still accurate.

# Step 3 — Bootstrap `.claude/rules/`

**When to run:** after step 2 — `docs/` must exist.
**Output:** `.claude/rules/` directory with 4–5 coding-standard files.
**Estimated time:** 30–60 minutes.
**Prerequisites:** `CLAUDE.md`, `project-info.md`, and `docs/` all committed.

---

## What this step produces

`.claude/rules/` is the **enforcement layer** — *how* to write code in this repo. Every file in this folder is:

- **Cited** by skills, agents, commands, and the `code-review` skill.
- **Numbered by section** (`§1`, `§2.3`, etc.) so other files can cite stable anchors.
- **Cross-linked** with `docs/` (which owns *what* and *why*) — they never duplicate.

Target files (conditional rows skip themselves when `project-info.md` says N/A):

```
.claude/rules/
├── backend_coding.md        # always, unless §4.1 is N/A
├── frontend_coding.md       # only if §4.2 is not N/A
├── security.md              # always
├── testing.md               # always
└── upgrade-policy.md        # always — version baselines + new-code guardrails
```

> **Critical separation:** rules tell contributors *how* to write code. They cite `docs/` for *what* the code must do. A rule that re-explains a business rule is duplication and will drift. Always cite, never paraphrase.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are bootstrapping `.claude/rules/` for this repository.

INPUT:
- Read `CLAUDE.md`, `project-info.md`, and the entire `docs/` tree.
- Read `claude-guidelines/reference/what-each-artifact-is-for.md` if present, for the rules-vs-docs separation principle.

GOAL:
Produce a coherent set of coding-standard files. Each file is a contract:
- contributors follow it,
- `code-review` skill (step 4) checks against it,
- skills/agents/commands (steps 4–6) cite it by section number.

Optimize for:
- enforceability — every "MUST" is a release-blocker; every "MUST NOT" describes a defect
- cite-don't-duplicate — when describing WHAT/WHY, link to `docs/` instead of restating
- testability — code reviewers can mechanically check each rule

TARGET FILES (omit conditional ones based on `project-info.md` §4):

.claude/rules/
├── backend_coding.md     (omit only if §4.1 is N/A — rare)
├── frontend_coding.md    (omit if §4.2 is N/A)
├── security.md           (always)
├── testing.md            (always)
└── upgrade-policy.md     (always)

WRITING CONVENTIONS:
- Every file has a one-line "what this file is" intro and a "status" note if the codebase isn't scaffolded yet.
- Sections are numbered `## 1.`, `### 1.1`, etc. — stable anchors. Never renumber when adding sections; insert with letters (`### 1.1a`) or append at end.
- Tables for matrices (forbidden/preferred, exceptions, severity, etc.).
- Cite `docs/` and other rule files with relative markdown links. NEVER paraphrase what's in `docs/`.
- Inline code examples in the language(s) from `§4`, NOT pseudocode.
- Use language like "MUST", "MUST NOT", "Never", "Prefer", "Avoid" — these map to severity (see `code-review` skill in step 4).
- Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.
- No emojis.

REQUIRED SECTIONS PER FILE:

### backend_coding.md
1. Project structure — link to `docs/architecture/` §3 for the module tree; state the cross-feature rules (e.g. "never import another feature's `service/` from `api/`").
2. Routing & controllers — framework-specific (JAX-RS / Express / FastAPI / …). URL constants, path conventions, response objects, per-endpoint required headers/middleware.
3. Service layer — interface vs. impl, naming, transaction boundaries, validation flow (which layers each guard runs in), what MUST NOT happen on the request thread.
4. Data models / entities — PK strategy, date/time handling, money columns (if `§13` mentions BigDecimal), relationship config, N+1 avoidance.
5. Data access — repository style (e.g. Panache Repository), locking policy, lock acquisition order (link `docs/business-rules/`), query building, return types, pagination.
6. DTOs — naming (`<Action><Noun>Request`, `<Noun>Response`), shape (records vs classes), "never expose entities" rule.
7. Object mapping — library (if any), null-value strategy for partial updates.
8. Exception handling — custom exception base, global mapper, status code mapping table, error-response shape.
9. Security configuration — link `security.md`; method-level authorization; CORS posture.
10. Pagination & sort safety — params, caps, sort whitelist (CRITICAL).
11. Logging — library, placeholder syntax, per-level guidance, forbidden content (secrets/PII/full Idempotency-Key per `security.md`).
12. Validation — library, creation vs update DTOs, custom validators.
13. Database migrations — link `docs/database/migrations.md`; forward-only policy; entity+migration in same PR rule; no auto-DDL.
14. Unit tests — link `testing.md`; framework, coverage floor, naming, mocking matrix at a glance.
15. Messaging (Kafka/RabbitMQ/etc.) — channel naming, producer pattern, consumer pattern, failure handling (DLQ). Omit if §4.4 is N/A.
16. WebSockets — endpoint pattern, broadcast model. Omit if no real-time channel in `§3`.
17. Configuration — config mechanism (Microprofile / env / dotenv / …), namespace convention.

### frontend_coding.md (conditional on §4.2)
1. Component structure — paradigm (standalone components / functional components / …), file naming, directory layout.
2. State management — boundary table (local UI / cross-component / cross-feature / server cache); state library policy.
3. API calls — network client, API path constants, per-call shape, error normalization, loading state, token-expiry handling.
4. Forms & validation — library, backend-aligned validation MATRIX (link to `docs/business-rules/`); no DOM manipulation for validation UI.
5. Routing & route protection — router setup, guards (functional only), role/condition mapping.
6. Styling — Tailwind / CSS modules / styled-components per §4.2; tokens; responsive utilities; UI library overrides.
7. Import ordering — group order with examples.
8. Constants — naming, environment config, no magic numbers.
9. Async patterns — Promise vs Observable; subscription lifecycle (e.g. `takeUntilDestroyed()`); cleanup.
10. Props / Component arguments — typed; no `any`.
11. Testing — link `testing.md §3`; co-located specs; query priority (data-test > role > text); NEVER by class.
12. Shared components catalogue — table for future inventory.
13. Domain-specific conventions — idempotency-key generation, money formatting, real-time stream subscription (one shared service).
14. Error boundaries — global handler; route-level fallback.
15. Framework-specific lifecycles — effects, cleanup, race-condition guards.
16. List rendering — stable keys; virtualization for large lists.
17. Accessibility floor — non-negotiable table (keyboard, labels, ARIA, contrast).
18. Bundle hygiene — devDependencies isolation; strip console logs; tree-shake imports.
19. Anti-patterns — table of don't/why/do-instead, 10+ rows.

### security.md
1. Secrets and configuration — no committed secrets; env-var loading; frontend-vs-backend env rules; log scrubbing.
2. Authentication — when JWT chosen, algorithm constraints; clock skew; verification; password hashing if applicable; account recovery; enumeration prevention. If `§8` says auth scheme is unspecified, mark this whole section as "applies once an auth ADR commits" and link `docs/decisions/`.
3. Authorization — default-deny posture; ownership/tenant checks; role escalation prevention.
4. Input validation & injection — boundary validation; SQL/JPQL injection; file upload (if applicable); XSS; open-redirect.
5. Transport & CORS — HTTPS-only; CORS allow-list; security headers (HSTS, CSP, X-Frame-Options, etc.); CSRF if cookie auth.
6. Sessions & token handling (frontend) — storage trade-offs table; header injection; expiry & logout; UX vs security.
7. Sensitive data exposure — DTO constraints; user-facing error messages; idempotency-key handling in logs.
8. Rate limiting & abuse — endpoint table (from `§8`); progressive back-off; distinction from fraud/observation rules.
9. Dependencies — scanning cadence; severity policy; legacy CVEs.
10. Secret scanning — pre-commit hook; rotation protocol.
11. Testing security-sensitive code — link `testing.md`; required test types (unauthenticated / wrong-tenant / replay / boundary / XSS payload).
12. Code-review checklist — checkbox list referenced by `code-review` skill in step 4. CRITICAL.

### testing.md
1. Coverage targets — table from `§4.5` floors per scope.
2. Backend testing — frameworks; mocking decision matrix; method naming; test DB setup (Testcontainers vs in-memory policy); exception assertions; parameterized & boundary tests; integration test profile; external API mocking; security test context.
3. Frontend testing — co-located specs; AAA layout; wrappers/providers; query priority; mocking the network client; regression requirements (XSS); async assertions.
4. Execution strategies — fast feedback commands; full CI commands.
5. What not to test — generated code; framework internals; private methods; trivial getters; framework behaviour.
6. Test discipline — no skipping without ticket; self-contained; one assertion theme; flaky tests are defects.

### upgrade-policy.md
1. Supported baselines — table from `§4`. One row per stack component (Language, Framework, ORM, DB, Cache, Messaging, FE framework, etc.). Columns: library, current target, status, source.
2. Migration posture — table of legacy patterns + their "new-code rule". Empty (`_none_` row) for greenfield.
3. Backend upgrade guardrails for new code — language idioms to prefer (e.g. records over Lombok, sealed interfaces, etc.); framework idioms (e.g. Quarkus extensions over raw libraries); package namespace policy (e.g. `jakarta.*` not `javax.*`).
4. Frontend upgrade guardrails for new code — language version, framework idioms (e.g. standalone components, signal inputs, functional guards/interceptors). Omit if `§4.2` is N/A.
5. When to break this policy — list the two conditions (critical CVE; new feature with version requirement) and the ADR procedure.
6. Accepted risks — link `docs/decisions/`; required fields for accepting a risk.

PROCESS:
1. Read every file in `docs/`. Note section numbers you'll cite.
2. Generate each rule file. Cross-link freely.
3. For greenfield projects, add this banner to each file's intro:
   > **Status:** the codebase is not yet scaffolded. Every rule cites either `docs/` or a section of `project-info.md`. Code examples use module names from `docs/architecture/`. Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.
4. End each file with no trailing summary section — rules don't need conclusions.

OUTPUT:
1. Create `.claude/rules/` with the appropriate files.
2. Print a tree listing.
3. Print a "Cross-link integrity check" section: scan each rule file and report any link to `docs/` or another rule that doesn't resolve.

DO NOT:
- Read or write any other files.
- Create any `.claude/skills/`, `.claude/commands/`, `.claude/agents/` — those are later steps.
- Invent rules that are not derivable from `project-info.md` + `docs/`.
- Re-state business rules from `docs/business-rules/` — cite them.
- Use emojis.
- Create a git commit.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| Every rule file has a numbered section table-of-contents at the top OR the sections are in stable numerical order | Open each file; scan headings. |
| `security.md` has a §12 code-review checklist | Open it; verify it's a checkbox list. |
| `upgrade-policy.md §1` baselines table matches `project-info.md §4` row-for-row | Diff them mentally. |
| No section paraphrases a `docs/business-rules/` page — always cites | grep each rule for the rule names from `docs/business-rules/`; they should appear inside markdown links, not as standalone prose. |
| Cross-link integrity check section is empty or short | Long = many broken links; rerun. |
| For greenfield: every file has the "Status: not yet scaffolded" banner | Open each. |

If any check fails, rerun in a fresh session.

---

## Commit

```bash
git add .claude/rules/
git commit -m "claude: add coding rules"
```

Then move to [step-4-bootstrap-skills.md](step-4-bootstrap-skills.md).

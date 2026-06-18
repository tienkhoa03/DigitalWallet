# Create Implementation Plan

PLANNING ONLY — Do NOT implement code. This command produces a structured markdown plan under [../../docs/plans/](../../docs/plans/) that a developer (or `/implement-plan`) will execute later. After the plan is approved, invoke `/implement-plan <path>`.

The plan must reflect the current project standards captured in [../rules/](../rules/) and the product / architecture contract in [../../docs/](../../docs/) and [../../CLAUDE.md](../../CLAUDE.md). If a rule or doc is missing, surface it as an Open Question — never invent answers.

## Step 1: Parse the input

Accept the task argument in any of these forms:

- Plain text — `add wallet deposit endpoint`.
- Ticket id — `WALLET-123`, `#42`.
- URL — a GitHub issue, Linear ticket, or PR link.

Extract the user's intent into a one-sentence task summary. If — and only if — the request is genuinely ambiguous (e.g. "wallet stuff", "fix the thing"), use `AskUserQuestion` to disambiguate **before** moving on. Specifically, ask only when one of these is unclear:

- Whether the change is backend, frontend, or both.
- Which feature module (`account`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`, `shared`, or a frontend feature folder).
- The FR/NFR being addressed when the task description does not pin one down.

Do NOT ask follow-up questions about style, naming, or testing — those are governed by the rule files in [../rules/](../rules/).

## Step 2: Invoke skills FIRST

Skills before research. Detect the surface area from the parsed task and invoke the matching skill(s) so they prime the planning context with project-specific procedure.

| Trigger | Skill | Purpose |
|---|---|---|
| Task mentions a REST endpoint, JAX-RS resource, service method, or any path under `backend/` | `Skill("backend-create-rest-api")` | Loads the vertical-slice procedure (inbound web adapter → application service → outbound persistence adapter → outbox → tests) and the canonical hexagonal project structure. |
| Task adds or substantially changes a backend Java class with branching logic | `Skill("backend-create-unit-test")` | Loads the JUnit 5 + Mockito scaffolding rules and the boundary / idempotency / lock-path test matrix. |
| Task adds a Vue route, page, form, or presentational component under `frontend/` | `Skill("frontend-implement-ui-component")` | Loads the Vue 3 + TypeScript + TanStack Query (Vue Query) + VeeValidate + Zod scaffolding rules. |
| Task touches both backend and frontend | Invoke both backend and frontend skills above | Plans the contract once and the two implementations consistently. |
| Pure documentation, ADR, or rule-file change | No code skill — read the corresponding doc / rule directly | Planning is mostly textual; no scaffold needed. |

Detection rules:

- **Backend signal** — any of: REST endpoint, JAX-RS, Quarkus, Kafka, outbox, ledger, wallet service, fraud rule, PFM aggregator, advisor backend, JPA, Flyway migration, Redis lock.
- **Frontend signal** — any of: Vue, route, page, form, Vue Query, Pinia store, Tailwind, WebSocket client, Zod schema.
- **Both** — anything that spans an end-to-end user flow (e.g. "deposit money", "open a budget", "show fraud alerts") implies both signals unless the task is explicitly scoped.

If neither signal fires (e.g. "update ADR 0002"), skip Step 2 and proceed.

## Step 3: Read project guidelines

Read in this order, only what is relevant to the parsed task — do not read every file every time:

1. [../../CLAUDE.md](../../CLAUDE.md) — always.
2. The matching rule file(s) for the surface area:
   - Backend work: [../rules/backend_coding.md](../rules/backend_coding.md).
   - Frontend work: [../rules/frontend_coding.md](../rules/frontend_coding.md).
   - Always: [../rules/security.md](../rules/security.md), [../rules/testing.md](../rules/testing.md), [../rules/upgrade-policy.md](../rules/upgrade-policy.md).
3. The relevant `docs/` pages — at least:
   - The feature's business rules under [../../docs/business-rules/](../../docs/business-rules/).
   - The API contract section in [../../docs/api/README.md](../../docs/api/README.md) when an endpoint is involved.
   - The architecture sections in [../../docs/architecture/README.md](../../docs/architecture/README.md) when modules / topics / consumers are involved.
   - Any ADR under [../../docs/decisions/](../../docs/decisions/) whose subject the task names (concurrency, JWT, outbox, LLM provider, etc.).

Note explicit citations from these files — every claim in the plan that came from a rule or doc must carry a `[path#anchor]` reference.

## Step 4: Research phase

Use the right tool for the breadth of the question:

- **Broad / open-ended** ("where do we currently handle wallet currency conversion?", "which modules touch the outbox?"): `Agent` with `subagent_type=Explore` and a question-shaped prompt. Do this in parallel for independent questions.
- **Targeted lookup** (a specific file, symbol, or path): `Glob`, `Grep`, or `Read` directly.
- **External Microsoft/Azure docs** (Quarkus on Azure, Azure Service Bus comparison, etc.): the `microsoft_docs_search` MCP tool.

Capture findings as plain notes — they will land in the plan's Reference Files and Risks sections.

## Step 5: Slugify the task

Produce `<slug>` from the task summary:

- Lowercase.
- Hyphen-separated.
- Strip punctuation and stop-words where it improves readability.
- Maximum 60 characters.
- Examples: `add-wallet-deposit-endpoint`, `wire-fraud-velocity-rule`, `frontend-budget-bucket-form`.

## Step 6: Ensure `docs/plans/` exists

Create [../../docs/plans/](../../docs/plans/) if it does not already exist. Do not touch any other directory.

## Step 7: Write the plan

Write the plan file at `docs/plans/implementation-plan-<slug>.md`. The sections below are MANDATORY and must appear in this order. Do not omit a section — if there is nothing to record, write "None at this time." so a future reader can tell the section was considered.

### 1. Header

A small metadata block at the top. Include:

- Title (single `# Implementation Plan: <task summary>`).
- Date — today's date (`YYYY-MM-DD`).
- Ticket — id or URL, or `n/a`.
- Story points / T-shirt size — `XS | S | M | L | XL`. **NEVER use wall-clock time** (no "2 hours", no "half a day") — the rule file [../rules/upgrade-policy.md](../rules/upgrade-policy.md) and [../../CLAUDE.md](../../CLAUDE.md) treat estimates as relative effort only.
- Milestone — feature epic from [../../project-info.md](../../project-info.md) or `n/a`.
- Assignees — `unassigned` if unknown.
- Affected modules — backend module names from [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering) and / or frontend feature folders.
- Suggested branch name — `feat/<slug>`, `fix/<slug>`, `chore/<slug>`, etc.

### 2. Context / Problem Statement

2–4 paragraphs. Cite the driving FR / NFR / ADR using `[../path](../path)`-style links. State what currently exists (or "spec — not yet implemented"), what the gap is, and the desired end state.

### 3. Scope

Two bullet lists:

- **In Scope** — what will change in this plan.
- **Out of Scope** — neighbouring concerns deliberately excluded (with a one-line reason).

### 4. Open Questions (MANDATORY)

A table — even if there are none, render the header and a single row "None — proceed." Status legend:

```
Status legend: Unanswered (open) | Answered (closed) | Deferred (tracked elsewhere)
```

| # | Question | Status | Resolution / Owner |
|---|---|---|---|

Use `AskUserQuestion` to resolve genuine ambiguities before approval; record the resolution in the row. **A plan with any row in `Unanswered` status MUST NOT be approved.** `/implement-plan` will refuse to run.

### 5. Technical Approach / Architecture Decisions

Describe how the change fits the existing architecture. Reference specific sections — e.g. "follows the synchronous money path described in [../../CLAUDE.md#synchronous-stream-money-path](../../CLAUDE.md#synchronous-stream-money-path) and the lock-acquisition order in [../rules/backend_coding.md §3](../rules/backend_coding.md#3-service-layer)". Include a Mermaid diagram only when the change crosses major boundaries (HTTP ↔ Kafka, frontend ↔ backend, new consumer path).

### 6. Applicable Rules, Skills & Agents

| Concern | Source |
|---|---|
| Coding rule | `[../rules/<file>.md#anchor](../rules/<file>.md#anchor)` |
| Skill to invoke | `Skill("<skill-name>")` |
| Agent to dispatch | `@<agent-name>` |

List every rule file, skill, and agent that `/implement-plan` will need to honour. This table is the contract the implementer reads first.

### 7. File Structure

ASCII tree of every directory that will be touched. Do not list unaffected directories. Example:

```
backend/
└── wallet/
    ├── api/
    ├── service/
    └── persistence/
frontend/
└── src/
    └── features/
        └── wallet/
```

### 8. Files to Modify / Create

| Module | Path | Action | Layer |
|---|---|---|---|
| `backend/wallet` | `backend/wallet/api/WalletResource.java` | Create | api |
| `backend/wallet` | `backend/wallet/service/WalletService.java` | Modify | service |

Use absolute paths from the repo root. `Action` is `Create | Modify | Delete`. `Layer` is `api | service | persistence | consumer | event | shared | route | feature | route-guard | docs | rule | migration`.

### 9. Progress Tracker

Phases as a checklist. Each item is tagged with the executing agent and the skill to invoke:

```
- [ ] Phase 1: Scaffold the JAX-RS resource — @backend-developer, Skill("backend-create-rest-api")
- [ ] Phase 2: Wire the application service + outbox + Redis lock — @backend-developer
- [ ] Phase 3: Add unit + integration tests — @backend-developer, Skill("backend-create-unit-test")
- [ ] Phase 4: Frontend form + Vue Query API client — @frontend-developer, Skill("frontend-implement-ui-component")
- [ ] Phase 5: Verify + review — orchestrator, Skill("backend-verify"), Skill("frontend-verify"), Skill("code-review")
```

Phases must be in dependency order: contract → backend skeleton → frontend wiring → tests → verification.

### 10. Acceptance Criteria

Checkbox format, every item objectively verifiable (a reviewer can say PASS/FAIL without judgement):

```
- [ ] `POST /wallets/{walletId}/deposits` returns 200 with the canonical `DepositResponse` envelope.
- [ ] `Idempotency-Key` header is required; replays return the original outcome.
- [ ] JaCoCo service-layer line coverage remains >= 80% (NFR4).
```

### 11. Security Considerations (MANDATORY)

For every change, map to the relevant section of [../rules/security.md](../rules/security.md). Cover at minimum:

- §1 secrets and configuration — any new env vars?
- §2 authentication — does the endpoint require a JWT?
- §3 authorization — role and ownership checks at both the inbound web adapter and the application service.
- §4 input validation & injection — `@Valid`, sort whitelist, no `v-html` on user input.
- §7 sensitive data exposure — DTO fields, log redaction.
- §8 rate limiting — does this need a new Redis token bucket?
- §11 testing security-sensitive code — which of (unauthenticated / wrong-role / wrong-tenant / replay / boundary / XSS / rate-limit / audit-log) tests apply?
- §12 code-review checklist — confirm each item is met or list the exceptions.

### 12. Testing Strategy

Cite [../rules/testing.md](../rules/testing.md). Specify:

- **Unit tests** — class list, framework (JUnit 5 + Mockito), boundary and exception cases.
- **Integration tests** — Testcontainers (Postgres / Kafka / Redis) scenarios, NFR test contexts (NFR1 concurrency, NFR2 outbox, NFR3 idempotency, NFR7 event time, NFR8 LLM isolation).
- **Frontend specs** — Vitest + @testing-library/vue, XSS regression, money-format assertion.
- **Coverage floor** — restate the NFR4 80% application-service-layer rule and any frontend coverage expectations.

### 13. Reference Files

Bullet list of existing files the implementer should read first — typically the closest sibling resource, the relevant business-rules page, the rule sections cited in §11, and any ADR mentioned in §2.

### 14. Risks & Dependencies

Two short lists:

- **Risks** — concurrency races, FX rate availability, LLM circuit state, materialised-view rebuild lag, etc. Each risk has a one-line mitigation.
- **Dependencies** — other plans / ADRs / external services that must land first.

## Step 8: Validate the plan

Before presenting, self-check:

- Does every section above exist and have content (or "None at this time.")?
- Are all file paths real (use `Read` to spot-check parent directories) or marked clearly as "to be created"?
- Are the phases in dependency order?
- Are all Open Questions either resolved or explicitly marked deferred?
- Does the Security Considerations section touch every relevant `security.md` chapter?
- Are skill names spelled exactly as they exist under [../skills/](../skills/) and agent names spelled exactly as they will exist under [../agents/](../agents/) (`@backend-developer`, `@frontend-developer` — these will be created in step 6 of the bootstrap; the names are fixed)?

Fix anything that fails the self-check before showing the file to the user.

## Step 9: Present and iterate

- Print the plan file path.
- Suggest the first skill the implementer will run.
- Use `AskUserQuestion` to collect feedback in structured form (e.g. "Is the scope correct?", "Are the open questions complete?", "Approve or revise?").
- Iterate on the file in place until the user approves.
- Do NOT proceed to implementation. Do NOT create a git commit. After approval, instruct the user to run `/implement-plan docs/plans/implementation-plan-<slug>.md`.

# Example — Digital Wallet, end-to-end

A worked example showing how the [Digital Wallet](../../README.md) project's `CLAUDE.md`, `docs/`, and `.claude/` were derived from the same `project-info.md` template (now distributed in this repo as the live artifacts under `/CLAUDE.md`, `/docs/`, `/.claude/`).

This is the canonical reference application of the playbook. Read it to see *what good output looks like* before running the bootstrap on your own project.

---

## The source spec

The project's product spec is at [../../README.md](../../README.md). Key facts:

- **Domain:** miniature financial system — wallets, deposits, withdrawals, P2P transfers, plus a fraud-detection engine.
- **Two streams:** synchronous core-banking (ACID, fast) + asynchronous fraud engine (Kafka).
- **Stack:** Java 21 + Quarkus backend; PostgreSQL + Flyway; Kafka via SmallRye Reactive Messaging; Redis for idempotency/locks; Angular 17+ frontend.
- **Non-negotiables:** pessimistic locking, ACID commits + commit-then-publish ordering, idempotency on transfers, fraud detection off the request thread, ≥80% service-layer test coverage.

---

## How the template was filled (representative excerpts)

If we were to reconstruct `project-info.md` from the current state of the repo, the key sections would look like this. Compare to [../01-project-info-template.md](../01-project-info-template.md) to see the mapping.

### §1 Project Identity

- **Project name:** Digital Wallet
- **One-line description:** An internal funds platform: users hold wallets, deposit/withdraw simulated funds, transfer to other users, while a separate fraud engine scores transactions asynchronously and pushes alerts to admins.
- **Primary value:** Demonstrates ACID-correct money movement under concurrent load AND decoupled real-time risk analysis that doesn't slow the transaction path.
- **Status:** greenfield (no source code committed; spec + scaffolding only).

### §3 Architecture style

- **High-level shape:** feature-based + layered, with two parallel streams (synchronous core banking + asynchronous fraud engine) decoupled by Kafka.
- **Synchronous vs. asynchronous boundaries:** transfer path is synchronous (ACID); fraud analysis is on Kafka consumer threads.
- **Deployment topology:** single Docker Compose stack (app + Postgres + Kafka + Redis + optional InfluxDB).
- **Real-time channel:** Quarkus WebSockets Next (server) ↔ browser WebSocket (client).

### §3.1 Module organization

```
backend/src/main/java/.../
├── wallet/         { api, service, persistence }
├── transaction/    { api, service, persistence, event }
├── fraud/          { consumer, rule, publisher }
├── admin/          { api, ws }
└── shared/         { idempotency, config }

frontend/src/app/
├── core/           { http, ws, auth }
├── features/wallet/    { pages, components, services, models }
├── features/admin/     { pages, components, services }
└── shared/         { ui primitives, pipes }
```

### §4.1 Backend stack

| Concern | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Quarkus 3.x LTS |
| API library | JAX-RS via RESTEasy Classic (`quarkus-resteasy`) — synchronous/blocking |
| ORM | Hibernate ORM with Panache Repository |
| Migrations | Flyway via `quarkus-flyway` (forward-only) |
| Messaging | SmallRye Reactive Messaging Kafka |
| Cache | Redis via `quarkus-redis-client` (idempotency, locks, fraud counters) |
| Build tool | Maven with Quarkus platform BOM |

### §6 Non-functional requirements (the load-bearing section)

| ID | Invariant | Enforcement layer |
|---|---|---|
| NFR1 | Balance updates use pessimistic locking (`SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`) | service layer + repository |
| NFR2 | Money cannot be created or destroyed; DB commit and Kafka publish do not silently diverge | service guard + DB CHECK + commit-then-publish ordering |
| NFR3 | Transfer endpoints require `Idempotency-Key` HTTP header; replays return cached outcome | idempotency middleware (Redis-backed) |
| NFR4 | ≥80% service-layer line coverage | JaCoCo gate in CI |
| NFR5 | Fraud analysis runs on Kafka consumer threads, never on the request thread | architectural — `wallet/` etc. cannot import `fraud/` |

### §10 Open architectural decisions

| # | Decision needed | Blocking? |
|---|---|---|
| 1 | RESTEasy Classic vs RESTEasy Reactive | resolved → ADR 0001 |
| 2 | Pessimistic locking vs Redisson distributed lock | resolved → ADR 0004 |
| 3 | Outbox vs commit-then-publish | resolved → ADR 0006 |
| 4 | Auth scheme (JWT vs session vs OIDC) | UNRESOLVED — blocks rule §9 in `backend_coding.md` |

---

## What each bootstrap step produced

### Step 1 → `/CLAUDE.md`

Output: [../../CLAUDE.md](../../CLAUDE.md). Notice:

- Under 100 lines.
- Tech stack stated as constraints ("Do not substitute Spring Boot…").
- Two-stream architecture explained in ≤8 bullets per stream.
- Five "Non-Negotiable Invariants" matching the NFRs above.
- Commands section is a placeholder because no build tooling is committed yet — exactly the greenfield behaviour described in [step 1's prompt](../prompts/step-1-bootstrap-claude-md.md).

### Step 2 → `/docs/`

Output: [../../docs/](../../docs/). Highlights:

- [docs/README.md](../../docs/README.md) — folder map. Notice the "docs/ describes WHAT and WHY; .claude/rules/ describes HOW" disclaimer at the top.
- [docs/architecture/README.md](../../docs/architecture/README.md) — eight numbered sections including the ASCII data-flow diagram in §2.
- [docs/business-rules/](../../docs/business-rules/) — one file per epic. [transfer-rules.md](../../docs/business-rules/transfer-rules.md) uses the rule/why/enforced-in/failure-mode/frontend-shortcut block for every rule.
- [docs/decisions/](../../docs/decisions/) — seven ADRs, one per row in §10 of the source spec, with the last marked Proposed (auth).
- [docs/api/README.md](../../docs/api/README.md) — endpoint catalog, every entry marked `(spec — not yet implemented)`.

### Step 3 → `/.claude/rules/`

Output: [../../.claude/rules/](../../.claude/rules/). Five files:

| File | Highlights |
|---|---|
| [backend_coding.md](../../.claude/rules/backend_coding.md) | 17 numbered sections from project structure → configuration. Cites `docs/business-rules/transfer-rules.md` for every commit-then-publish, locking, idempotency rule. |
| [frontend_coding.md](../../.claude/rules/frontend_coding.md) | 19 sections. The "anti-patterns" table at §19 is the quick gut-check during reviews. |
| [security.md](../../.claude/rules/security.md) | 12 sections; §12 is the code-review checklist the `code-review` skill uses. |
| [testing.md](../../.claude/rules/testing.md) | Mock decision matrix at §2.2; "what NOT to test" at §5; test discipline at §6. |
| [upgrade-policy.md](../../.claude/rules/upgrade-policy.md) | §1 baselines table is the single source of truth for any version reference in other rule files. |

### Step 4 → `/.claude/skills/`

Output: [../../.claude/skills/](../../.claude/skills/). Seven skill folders:

- `backend-create-rest-api/` — scaffolds a full vertical slice (migration → resource → test).
- `backend-create-unit-test/` — JUnit 5 + Mockito test generator.
- `backend-verify/` — detect-and-skip preflight, then compile → test → coverage pipeline.
- `frontend-implement-ui-component/` — Angular standalone component + Reactive Form + spec.
- `frontend-verify/` — passes `--watch=false --browsers=ChromeHeadless` to prevent hangs.
- `code-review/` — loads only the rules that match the changed-file patterns.
- `create-merge-request/` — GitHub via `gh`; gates every branching decision through `AskUserQuestion`.

### Step 5 → `/.claude/commands/`

Output: [../../.claude/commands/](../../.claude/commands/). Two commands:

- [make-plan.md](../../.claude/commands/make-plan.md) — writes a plan to `docs/plans/implementation-plan-<slug>.md`. Mandatory sections include Open Questions and Security Considerations.
- [implement-plan.md](../../.claude/commands/implement-plan.md) — dispatches to backend/frontend agents in parallel; final verify + code-review; structured report.

### Step 6 → `/.claude/agents/`

Output: [../../.claude/agents/](../../.claude/agents/). Two agents:

- [backend-developer.md](../../.claude/agents/backend-developer.md) — senior Quarkus engineer; bottom-up workflow (migration → entity → repo → DTO → service → resource → test); self-review checklist of 12 rule-cited items.
- [frontend-developer.md](../../.claude/agents/frontend-developer.md) — senior Angular engineer; top-down workflow (route → page → child → service → spec).

Both agents have a `tools:` line that includes `Agent` so they can delegate further; both explicitly exclude the other's domain in the `description`.

---

## How the artifacts compose in a real workflow

Suppose a developer wants to add a new feature: **"GET /wallets/{id}/transactions with pagination and filtering"**.

```
1. Developer types: /make-plan add transaction history endpoint with pagination

2. /make-plan reads:
   - CLAUDE.md (tech stack)
   - .claude/rules/backend_coding.md (esp. §10 pagination & sort safety)
   - docs/business-rules/wallet-rules.md (auth, ownership rules)
   - docs/api/README.md (endpoint catalog format)
   It invokes Skill("backend-create-rest-api") to lock the layer order.
   Writes docs/plans/implementation-plan-add-transaction-history-endpoint.md.

3. Developer reviews the plan, answers any Open Questions, runs:
   /implement-plan docs/plans/implementation-plan-add-transaction-history-endpoint.md

4. /implement-plan reads the plan, delegates to:
   - @backend-developer (executes phases 1–3: migration + entity + repo + service + resource + test)
   No frontend changes → no @frontend-developer dispatch.

5. @backend-developer invokes Skill("backend-create-rest-api") to scaffold, then
   Skill("backend-create-unit-test") for the test, then Skill("backend-verify").

6. /implement-plan runs Skill("code-review") on the full diff.
   Reports PASS/FAIL with rule-cited findings.

7. Developer reviews, then runs Skill("create-merge-request") to open the PR.
```

Notice the **layering**: command composes agent + skills; agent composes skills + rules; skills cite rules; rules cite docs. Every layer references the one below; nothing skips upward.

---

## Take-aways for applying the playbook to your own project

1. **Be ruthless in §6 (NFRs).** Those become CLAUDE.md invariants, business-rule pages, code-review checklist items, and test guidance. Vague NFRs → vague enforcement → no enforcement.
2. **Don't pre-decide what you haven't decided.** The Digital Wallet's auth scheme is explicitly unresolved (§10 row 4). Every rule that depends on it is stubbed with `// TODO(security): wire @RolesAllowed once auth ADR lands`. That is correct — fictional JWT code would be worse.
3. **Mark `(verify)` aggressively.** For greenfield, *most* references to file paths and version numbers are aspirational. Marking them keeps Claude from grepping for files that don't exist.
4. **Cross-link until it feels excessive.** Every rule that touches a business rule cites the business-rule page; every skill that uses a rule cites the rule section. The validation pass in step 7 catches breakage.
5. **Commit per step.** Seven commits give you seven rollback points. Bundling them into one mega-commit makes the bootstrap unreviewable.

---

## See also

- The live artifacts in this repo are the worked example. Skim them as you read this page:
  - [../../CLAUDE.md](../../CLAUDE.md)
  - [../../docs/](../../docs/)
  - [../../.claude/](../../.claude/)
- [../README.md](../README.md) for the playbook overview.
- [../03-initialization-workflow.md](../03-initialization-workflow.md) for the step-by-step.
- [../reference/what-each-artifact-is-for.md](../reference/what-each-artifact-is-for.md) for the mental model.

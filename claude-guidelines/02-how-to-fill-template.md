# How to Fill the Project-Info Template

Field-by-field guidance for [01-project-info-template.md](01-project-info-template.md). Read this **once** before filling; refer back when a section feels ambiguous.

> **Golden rule.** The template is read by Claude as ground truth. If you write "*JWT, probably*" then Claude will produce code that assumes JWT was decided. Vagueness compounds. Be specific or mark the question as `⏳ Deferred` in §16.

---

## General writing style

- **Concrete > abstract.** "Postgres 16 + Flyway, schema in `db/migration/`" beats "a relational database with migrations".
- **One sentence > one paragraph.** The template is a brief. Save discussion for ADRs.
- **Mark unknowns explicitly.** `(verify)` for "we said this but haven't proven it" and `(unspecified — to be decided in ADR §10.N)` for "we genuinely haven't decided".
- **No marketing language.** "Best-in-class real-time fraud detection" is not actionable. "Detect >5 transactions per account per minute" is.
- **Number everything in §5 and §6.** Downstream rules cite them by ID.

---

## §1 Project identity

| Field | Pitfall | Good answer |
|---|---|---|
| **One-line description** | A sales line. | "An internal funds platform: users hold wallets, deposit/withdraw simulated funds, transfer to other users, while a separate fraud engine scores transactions asynchronously and pushes alerts to admins." |
| **Primary value** | "Make money". | "Demonstrates ACID-correct money movement under concurrent load **and** decoupled real-time risk analysis that doesn't slow the transaction path." |
| **Status** | "Building". | One of: `greenfield`, `retrofitting an existing repo`, `partial implementation`. The prompts behave differently for each. |

---

## §2 Stakeholders & users

- **Personas** are *what* users want to do, not *who they are demographically*.
- **Roles** are the system's authorization concept. If you don't have one yet, write `N/A — single role for MVP` and put "decide auth model" in §10.
- If you have **no users yet** (purely internal tooling), list the human operator (DevOps engineer, ops team).

---

## §3 Architecture style

This is the hardest section because it's the most consequential. Be deliberate.

- **High-level shape:** pick *one* primary label and stick to it. "Hexagonal microservices with serverless event handlers" is three labels — it's nothing.
- **Module organization (§3.1):** sketch a target tree even if no code exists. The bootstrap will create rules that reference these paths. Common patterns:
  - **Feature-based + layered** — group by feature (`wallet/`, `fraud/`), then standard layers inside (`api/`, `service/`, `persistence/`). Best for medium projects.
  - **Layer-first** — `controllers/`, `services/`, `repositories/` at the root. Best for small projects with no clear feature boundaries.
  - **Hexagonal / ports-and-adapters** — `domain/`, `application/`, `infrastructure/`. Best when domain logic is the centre of gravity.
- If you genuinely don't know, default to **feature-based + layered** — it's the easiest to refactor later.

---

## §4 Tech stack

### 4.1 Backend

- **Always pin a major version.** "Java 21" not "Java 17+". The prompts will create an `upgrade-policy.md` table from this.
- **Name the specific library family**, not the generic concept. "JAX-RS via RESTEasy Classic" not "REST". "Hibernate ORM with Panache Repository" not "an ORM".
- **State the build tool.** Maven vs Gradle vs pnpm changes the verify scripts the bootstrap generates.

### 4.2 Frontend

- If there is no frontend, write `N/A — backend-only` and skip — the prompts will not generate `.claude/rules/frontend_coding.md`.
- **Form library** matters more than people think — it drives the validation rule generation. "Reactive Forms" vs "React Hook Form" produces different rule text.

### 4.3 Persistence

- **One row per data store.** Don't combine "Postgres + Redis" into one row — they have different correctness properties.
- **Constraint column** = the non-negotiable: "ACID, BigDecimal money", "ephemeral cache only, never source of truth", etc.

### 4.4 Messaging

- If there is no async messaging, write `N/A` for §4.4. The bootstrap will skip the consumer/emitter sections of the rules.
- **Topic list** is the seam between services — name every topic that exists in the design.

### 4.5 Testing

- The **floor** column is enforced by `code-review` and `verify` skills. Pick a number you'll defend.
- ≥80% service-layer line coverage is the conventional floor for financial / safety-critical code.
- For pure CRUD apps, 60–70% is more honest — the prompts will not invent ceremony tests to hit a fake number.

### 4.6 Deployment

- **CI/CD:** if you don't have one, write `N/A — local development only`. The prompts will then not generate CI-specific verification commands.

---

## §5 Functional requirements

- Number every requirement: `FR<epic>.<n>`.
- Phrase each as **"users can <verb> <thing>"** or **"the system <verb>s when <condition>"**.
- **Avoid implementation detail** here — that's for §3, §4, and the eventual code. "FR1.3 — users can transfer funds to another user by user ID or account number" is right. "FR1.3 — implement a `POST /transfers` endpoint that calls `TransferService.transfer()` with `LockModeType.PESSIMISTIC_WRITE`" is wrong.

---

## §6 Non-functional requirements / invariants

This is the **most important section** for AI assistance. NFRs become:

- the **Non-Negotiable Invariants** block in `CLAUDE.md`,
- the row-by-row enforcement matrix in `docs/business-rules/`,
- the test cases the `code-review` skill checks.

Rules of thumb:

- **One sentence per NFR.** "Balance updates use pessimistic locking" beats "We should consider using locks in some way to manage concurrency."
- **Name the enforcement layer.** Where does it get enforced? Without that, the rule is a wish.
- **Avoid impossibilities.** "100% uptime" is not an NFR; "≤30s outage budget per quarter" is.
- **Pick 3–7 invariants.** Below 3 means you haven't thought hard enough; above 7 means you're listing nice-to-haves.

---

## §7 External integrations

- If you have no externals, write `N/A` and the bootstrap skips the resilience/circuit-breaker rules. Don't pad.
- **Failure handling** is the most-skipped column and the most-important. "Circuit breaker, fallback to rule-based responses" is better than blank.

---

## §8 Security baseline

- **Auth scheme:** if you genuinely haven't decided, write `unspecified — to be decided in ADR §10.N` and reference §10. The bootstrap will produce guard stubs with `// TODO(security): wire @RolesAllowed once auth ADR lands` instead of inventing JWT logic.
- **PII handled:** list the actual fields. "Email, full name, last 4 of account number, phone" is actionable; "user data" is not.
- **Compliance constraints:** the answer changes the rules. PCI-DSS, HIPAA, GDPR each impose different defaults.

---

## §9 Domain glossary

- One row per term that has a **product-specific meaning** that differs from common English.
- **Don't define common terms.** "Database — a place to store data" is wasted.
- **Do define overloaded terms.** "Wallet" might be different from another team's "Wallet". State your meaning here.

---

## §10 Open architectural decisions

- List **every** decision you know is pending. The bootstrap will seed empty ADRs at `docs/decisions/000N-<slug>.md` so they're hard to forget.
- "Blocking?" column tells the bootstrap what to mark as `(verify)` vs. what to write definitively. A blocking decision means rules referencing it will be stubs until decided.

---

## §11 Explicit non-goals

- **Critical.** Without this section, Claude will infer scope from §5 and over-generate. State what's *not* being built.
- Use the test "would a reasonable stakeholder be surprised this isn't included?" — if yes, list it.

---

## §12 Development workflow

- The bootstrap uses **branch model** + **PR style** to generate the `create-merge-request` skill correctly.
- **Pre-commit hooks** influence whether the `code-review` skill recommends running `gitleaks` etc.

---

## §13 Coding conventions

Keep this **short**. One bullet per convention. The detailed expansion happens in `.claude/rules/<lang>_coding.md` during step 3 — don't pre-empt.

Common high-level decisions worth stating:

- **Money handling** (BigDecimal vs minor units vs float) — if financial.
- **Timestamps** (UTC + offset vs naive local) — if temporal.
- **Error model** (typed hierarchy vs Result/Either vs language native).
- **DTO policy** (separate from entities, never expose entities).
- **Logging library + sensitive-field policy.**

---

## §14 Environment & configuration

- One row per env variable that **will be read at runtime**.
- Don't include build-time variables (those go in CI config docs, not here).
- The bootstrap uses this to populate the per-environment table in `docs/architecture/`.

---

## §15 Reference materials

- The product spec link **must be stable**. Don't link to a Notion page that gets renamed weekly. Copy the spec into the repo if it's volatile elsewhere.
- Prior-art links help ADRs cite real evidence rather than opinion.

---

## §16 Open questions

- The bootstrap will **refuse** to generate rules that depend on `❓ Unanswered` questions. Either answer them, defer them with reason, or remove them.
- Status legend (matches the convention in `docs/plans/`):
  - `❓ Unanswered` — blocking.
  - `✅ Answered` — answer is in the row's notes column.
  - `⏳ Deferred` — explicitly postponed with a one-line reason.

---

## Common pitfalls

| Pitfall | Why it bites | Fix |
|---|---|---|
| Listing 12 NFRs to look thorough | Most aren't enforceable; rules dilute. | Cut to 3–7 that you'd genuinely block a PR on. |
| "We'll figure out auth later" with no §10 entry | Bootstrap generates random JWT or session-cookie code. | Add an entry to §10 and an `(unspecified)` line in §8. |
| Frontend and backend listed as one mega-row | Rules can't differentiate, end up mushy. | Keep §4.1 and §4.2 cleanly separated. |
| Spec lives in a chat thread, not §15 | Drift — Claude regenerates from outdated text. | Copy the spec into `docs/_archive/spec.md` and link from §15. |
| `Idempotency` mentioned in §5 but not §6 | Rule never enforces it, code skips it. | Promote it to an NFR row. |
| Domain term in §9 with a vague definition | Generated code uses the term inconsistently. | Tighten the definition; reject "TBD". |
| §10 has 15 entries marked Blocking | Bootstrap can't proceed. | Resolve or defer aggressively — most "open questions" are actually preferences. |

---

## Done-check before running the bootstrap

You're ready when:

- [ ] Every `<PLACEHOLDER>` is replaced.
- [ ] Every `// EXAMPLE` comment is removed.
- [ ] §5 and §6 are numbered.
- [ ] §16 has zero `❓ Unanswered` entries that are also `Blocking` in §10.
- [ ] §9 has at least 3 terms (every project has at least a few).
- [ ] §11 has at least 2 entries (every project excludes something).

If any check fails, fix it before proceeding to [03-initialization-workflow.md](03-initialization-workflow.md). Time spent here is time saved across the next seven steps.

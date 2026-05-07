# Create Implementation Plan

Generate a codebase-aware implementation plan document for a given task, grounded in the project's established rules and skills.

> **PLANNING ONLY** — Do NOT implement code. After the plan is approved, invoke `/implement-plan` in a separate session.

---

## Step 1: Parse Input

Input: `$ARGUMENTS`

Accepted forms:

- Plain-text task description.
- Issue / ticket number (with or without `#`).
- Full issue tracker URL.
- Optional hints (similar features, design URLs, files to look at).

### When to use AskUserQuestion

Use `AskUserQuestion` only when the input is genuinely ambiguous:

- `$ARGUMENTS` is empty or a single vague word.
- The task could land in either `backend/` or `frontend/` (or both) and the user didn't say.
- A feature is named with no context and no ticket reference.

Ask for the missing pieces only — do not interrogate.

---

## Step 2: Invoke Skills FIRST (MANDATORY)

**Critical:** Invoke applicable skills BEFORE research or writing. Do NOT skip.

| Trigger | Skill | Purpose |
|---|---|---|
| The plan will introduce a new REST endpoint or vertical slice | `Skill("backend-create-rest-api")` | Confirms the layer order and templates the agent will follow |
| The plan will add a new Angular component, page, or form | `Skill("frontend-implement-ui-component")` | Confirms the component conventions and form-validation matrix |
| The plan involves adding tests to an existing class | `Skill("backend-create-unit-test")` | Confirms test naming, AssertJ, mock decision matrix |
| Verification will be needed at the end | `Skill("backend-verify")` and/or `Skill("frontend-verify")` | Locks in the exact verify pipeline |
| The plan will land via PR | `Skill("create-merge-request")` | Locks in the branch / push / PR flow |

### Detection rules

- If `$ARGUMENTS` mentions "endpoint", "API", "controller", "resource", or "migration" → backend skills.
- If `$ARGUMENTS` mentions "page", "form", "component", "UI", "screen", or "dashboard" → frontend skills.
- If `$ARGUMENTS` mentions both → invoke both sets; the plan will require parallel agent dispatch in `/implement-plan`.

---

## Step 3: Read Project Guidelines

Always read:

- [CLAUDE.md](../../CLAUDE.md)
- [.claude/rules/security.md](../rules/security.md)

Read module rules based on affected module(s):

- Backend → [.claude/rules/backend_coding.md](../rules/backend_coding.md)
- Frontend → [.claude/rules/frontend_coding.md](../rules/frontend_coding.md)
- Tests → [.claude/rules/testing.md](../rules/testing.md)
- Versions → [.claude/rules/upgrade-policy.md](../rules/upgrade-policy.md)

Fact-check against `docs/`:

- [docs/api/](../../docs/api/) — endpoint contracts
- [docs/database/](../../docs/database/) — table schemas (if persistence is touched)
- [docs/business-rules/](../../docs/business-rules/) — domain rules the change must enforce
- [docs/architecture/](../../docs/architecture/) — module boundaries and the two-stream flow
- [docs/decisions/](../../docs/decisions/) — ADRs that constrain design

---

## Step 4: Research Phase

Use `Agent` with `subagent_type=Explore` for broad codebase research. Use `Glob` / `Grep` / `Read` for targeted lookups.

Research tasks:

1. Identify affected modules (`backend/`, `frontend/`, both, or cross-cutting like `docs/`).
2. Find similar patterns — launch `Explore` agents in parallel when multiple modules are involved.
3. Check existing tests for naming + style conventions (currently: rules-based — see [testing.md](../rules/testing.md)).
4. Identify dependencies: new migrations, new packages, cross-module API contracts, breaking changes.

---

## Step 5: Slugify the Task Title

Lowercase the title, hyphenate spaces, collapse consecutive hyphens, trim, truncate to 60 chars.

Example: `Implement P2P transfer endpoint with idempotency` → `implement-p2p-transfer-endpoint-with-idempotency`.

---

## Step 6: Create the Output Directory

```bash
mkdir -p docs/plans
```

---

## Step 7: Write the Plan Document

Write to: `docs/plans/implementation-plan-<slug>.md`

Required sections, in this order:

### 1. Header

- Title (≤ 80 chars).
- Generation date (today, absolute date).
- Issue / ticket link (or `—` if none).
- Story points / T-shirt size (S / M / L / XL — never wall-clock time).
- Milestone / target release (or `—`).
- Assignees (or `—`).
- Affected modules: `backend` / `frontend` / `cross-module`.
- Suggested branch name: `feature/<slug>` (or `fix/<slug>` for bug fixes).

### 2. Context / Problem Statement

2–4 paragraphs. Why is this task being done now? What constraint or user need motivates it? Cite the relevant FR / NFR from [README.md](../../README.md) or the ADR that drives it.

### 3. Scope

- **In Scope** — explicit bullets.
- **Out of Scope** — explicit bullets. If a stakeholder might reasonably expect something to be in scope but it isn't, list it here.

### 4. Open Questions (MANDATORY)

| # | Question | Source | Answer | Status |
|---|---|---|---|---|
| 1 | … | rule §X / docs path / spec section | … | ❓ Unanswered |

Status legend: `❓ Unanswered` | `✅ Answered` | `⏳ Deferred`. The plan cannot be approved with `❓ Unanswered` items remaining unless they are explicitly `⏳ Deferred` with a reason.

### 5. Technical Approach / Architecture Decisions

Reference specific rule sections and existing files. Include a Mermaid sequence diagram when the change touches the two-stream flow (request path + Kafka consumer + WebSocket alert). State which ADR is reaffirmed or amended.

### 6. Applicable Rules, Skills & Agents

| Concern | Rule / Skill / Agent |
|---|---|
| Backend implementation | `@backend-developer` + `Skill("backend-create-rest-api")` |
| Frontend implementation | `@frontend-developer` + `Skill("frontend-implement-ui-component")` |
| Coverage | `Skill("backend-verify")` / `Skill("frontend-verify")` |
| Final review | `Skill("code-review")` |
| Branch + PR | `Skill("create-merge-request")` |

### 7. File Structure

ASCII tree of the directories that will be touched.

### 8. Files to Modify / Create

Grouped by module:

| Module | Path | Action | Layer |
|---|---|---|---|
| backend | `backend/src/main/resources/db/migration/V<n>__create_…sql` | Create | migration |
| backend | `backend/src/main/java/.../wallet/persistence/Wallet.java` | Create | entity |
| frontend | `frontend/src/app/features/wallet/pages/transfer-form/transfer-form.component.ts` | Create | page |

### 9. Progress Tracker

Phases as a checklist, each tagged with the executing agent and skill:

```
- [ ] Phase 1 — Backend migration + entity     · @backend-developer · Skill("backend-create-rest-api")
- [ ] Phase 2 — Backend service + resource     · @backend-developer · Skill("backend-create-rest-api")
- [ ] Phase 3 — Backend unit tests              · @backend-developer · Skill("backend-create-unit-test")
- [ ] Phase 4 — Frontend service + page        · @frontend-developer · Skill("frontend-implement-ui-component")
- [ ] Phase 5 — Verify both modules             · orchestrator · Skill("backend-verify"), Skill("frontend-verify")
- [ ] Phase 6 — Code review                     · orchestrator · Skill("code-review")
```

Use `[x]` when complete, `[SKIP]` with a reason when intentionally not done.

### 10. Acceptance Criteria

Checkbox format. Each criterion must be objectively verifiable (a test that passes, an endpoint that returns a status code, a UI element with a `data-test` attribute).

### 11. Security Considerations (MANDATORY)

Map every change to the relevant section of [.claude/rules/security.md](../rules/security.md):

- Authentication / authorization → §2, §3
- Input validation → §4
- Logging of sensitive data → §1.4, §7.3
- Rate limiting if a new endpoint → §8
- Code review checklist coverage → §12

If a section is genuinely N/A, state so with one-line justification — never omit a section silently.

### 12. Testing Strategy

- Unit tests planned (per module).
- Integration / Testcontainers tests planned.
- Frontend specs (render + XSS regression).
- Coverage floor: service-layer ≥ 80 % per [NFR4](../../docs/testing/README.md).

### 13. Reference Files

Bullet list of existing files the implementer should read first.

### 14. Risks & Dependencies

- Cross-module API contract risk (backend & frontend must agree on shapes).
- Migration ordering vs. open PRs.
- Open ADRs that block the plan (e.g., auth scheme not yet committed → guards stay stubbed).

---

## Step 8: Validate the Plan

Before presenting:

1. Does the plan cover every aspect of the task?
2. Do the file paths and patterns actually exist (or land in the right new place) and match the rules?
3. Are the phases in logical sequence (migration before entity, entity before service, service before resource, resource before frontend)?
4. Is every ambiguous point captured in Open Questions?
5. Is Security Considerations filled with specific rule references?
6. Does every phase name its executing agent + skill?

---

## Step 9: Present and Iterate

- Print the full path to the generated document.
- State the first skill the implementer should invoke (usually `Skill("backend-create-rest-api")` or `Skill("frontend-implement-ui-component")`).
- Use `AskUserQuestion` to collect feedback. Options to offer: approve, answer open questions, revise scope, revise phases, add detail, reject.

---

## Important Rules

- DO invoke skills FIRST.
- DO use `AskUserQuestion` when input is ambiguous.
- DO always include Open Questions and Security Considerations sections.
- DO ground the plan in actual patterns from the codebase (or, while no code exists yet, from `.claude/rules/`).
- DO name specific skills and agents per phase.
- DO NOT implement code — planning only.
- DO NOT invent scope or add assumptions.
- DO NOT include wall-clock time estimates — use story points or T-shirt sizes (S / M / L / XL).
- DO NOT create git commits.

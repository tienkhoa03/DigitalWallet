# Implement Plan

Execute an implementation plan from a `.md` file, delegating work to specialized developer agents that run in parallel where possible.

## Input

The user provides: `$ARGUMENTS`

Expected: a path to a plan file (e.g., `docs/plans/implementation-plan-<slug>.md`). Resolve relative paths from the repository root.

---

## Step 1: Validate and Parse the Plan

1. Read the file. If missing or not `.md`, inform the user and stop.
2. Extract: Context, Scope, Technical Approach, Files to Modify/Create, Progress Tracker, Acceptance Criteria, Testing Strategy.
3. Print a brief summary: title, number of phases, modules affected, suggested branch name.

If the plan still has `❓ Unanswered` Open Questions, stop and tell the user — those must be resolved before implementation begins.

---

## Step 2: Categorize Tasks by Module

Classify every task into:

- **Backend tasks** → delegate to `@backend-developer`
- **Frontend tasks** → delegate to `@frontend-developer`
- **Cross-module / config / docs** → handled by the orchestrator (this command)

Print the categorization before proceeding.

---

## Step 3: Explore Shared Context

Before dispatch:

1. Read the plan fully — agents need complete context, they do not share memory with each other.
2. Read every file referenced in the plan's "Reference Files" section so you can quote shapes back to the agents.
3. Read any API contract pages in [docs/api/](../../docs/api/) the plan touches — both agents must agree on the request/response shapes and the `Idempotency-Key` contract.
4. Note cross-module dependencies (shared DTO shapes, enum values, error keys) and prepare to include them verbatim in each agent's prompt.

---

## Step 4: Dispatch to Developer Agents

**Critical:** When multiple agents are needed, spawn them in **a single message with multiple `Agent` tool calls** so they run concurrently. Do NOT spawn sequentially.

Each agent prompt must include:

1. **Full plan context** — Problem Statement, Scope, Technical Approach, Acceptance Criteria.
2. **That agent's specific subset of tasks**, with the plan's phase numbers.
3. **Cross-module dependencies it must honor** — shared API contracts, shared types, shared error keys. Include the literal shape, do not paraphrase.
4. **Relevant rules and docs** — `.claude/rules/<domain>_coding.md`, `.claude/rules/security.md`, `.claude/rules/testing.md`, `docs/business-rules/*` pages, `docs/decisions/*` ADRs.
5. **Verification command** — `Skill("backend-verify")` or `Skill("frontend-verify")` at the end.
6. **Explicit "DO NOT create git commits" instruction.**

If only one module is affected, spawn only that one agent. Do not invoke the other.

---

## Step 5: Handle Cross-Module Tasks

After agents complete, handle anything that doesn't belong to a developer agent yourself:

- `docs/` updates the plan calls for (e.g., a new endpoint row in `docs/api/README.md`, a new business-rules row in the enforcement matrix).
- Root-level config changes (Compose stack, env files, CI workflow).
- Integration wiring that crosses both modules and requires the orchestrator to view both diffs at once.

---

## Step 6: Verify the Combined Result

Run, in order:

1. `Skill("backend-verify")` — if backend was touched.
2. `Skill("frontend-verify")` — if frontend was touched.
3. `Skill("code-review")` — always, against the full diff.

If any verify step FAILs, route the failure back to the responsible agent with the specific error excerpt. Do not retry blindly.

---

## Step 7: Report

Print this exact shape:

```
## Implementation Report

**Plan:** <path-to-plan>
**Title:** <plan-title>
**Execution mode:** parallel (N agents) | sequential (1 agent) | orchestrator-only

### Agent Results

#### @backend-developer
- Phases completed: <list>
- Files touched: <list>
- Verification: PASS / FAIL (one-line detail)

#### @frontend-developer
- Phases completed: <list>
- Files touched: <list>
- Verification: PASS / FAIL (one-line detail)

### Cross-Module Tasks
- <task>: done | not-done (reason)

### Verification Results

| Step | Status | Detail |
|---|---|---|
| backend-verify | PASS / FAIL / SKIPPED | … |
| frontend-verify | PASS / FAIL / SKIPPED | … |
| code-review | PASS / FAIL | block-count, warn-count |

### Acceptance Criteria
- [x] <criterion> — addressed
- [ ] <criterion> — not addressed (reason)

### Issues Encountered
- <issue and how it was resolved, or why it remains>

### Next Steps
- Changes are uncommitted — review the diff.
- Suggested follow-up: `Skill("create-merge-request")` once you've reviewed.
```

---

## Important Rules

- **DO NOT create git commits.** Leave changes uncommitted for the user to review.
- **DO spawn agents in parallel** when multiple modules are affected — single message, multiple `Agent` tool calls.
- **DO give each agent the full plan context** — they don't share memory and won't see the plan otherwise.
- **DO include cross-module contracts in both prompts** when both modules are touched. Quote the literal shape.
- **DO invoke `Skill("code-review")`** as the final step before reporting done.
- **DO implement fully** without pausing between phases for confirmation; the user already approved the plan.
- **DO NOT skip a verify step** because it would slow you down — verifies are the contract.
- **DO NOT modify the plan file** during execution — annotate the Progress Tracker only at the end, in the report.
- If only one module is affected, spawn only one agent.
- Always print the Step 7 report.

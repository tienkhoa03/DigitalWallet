# Implement Plan

IMPLEMENTING — Execute an implementation plan from a `.md` file under [../../docs/plans/](../../docs/plans/), delegating work to specialized developer agents in parallel where possible. This command writes code, edits files, and runs verification skills. It does NOT create git commits — that is the user's call, typically via the `create-merge-request` skill.

The plan is the source of truth. Do not re-derive scope, re-decide architecture, or relitigate open questions. The user has already approved the plan; the job is to execute it faithfully.

## Step 1: Validate and parse the plan file

The argument is the path to a plan markdown file (typically `docs/plans/implementation-plan-<slug>.md`).

Reject and stop immediately if any of these are true:

- The path is missing or the file does not exist.
- The file does not end in `.md`.
- The "Open Questions" table contains any row in `Unanswered` status.

Parse the plan and extract:

- The task summary (header).
- The affected modules and suggested branch name.
- The Files to Modify / Create table.
- The Progress Tracker phases and their tagged agents / skills.
- The Acceptance Criteria checklist.
- The Reference Files list.
- The Security Considerations section.
- The Testing Strategy section.

If parsing fails (a required section is absent), stop and tell the user the plan is malformed — link back to `/make-plan` to regenerate it.

## Step 2: Categorize tasks by module

Walk the Progress Tracker and the Files to Modify / Create table, sorting each phase into one of:

- **Backend phase** — any path under `backend/`, any Flyway migration, any backend test, any ADR about backend behaviour. Dispatched to `@backend-developer` (will be created in step 6 of the bootstrap).
- **Frontend phase** — any path under `frontend/`, any frontend test, any frontend doc. Dispatched to `@frontend-developer` (will be created in step 6 of the bootstrap).
- **Cross-module orchestrator phase** — anything in [../../docs/](../../docs/), root config (`pom.xml`, `package.json`, `docker-compose.yml`, `application.properties`), shared contract files, and any phase that explicitly spans backend and frontend (e.g. "agree on `errorKey` for `wallet.duplicate_label`"). The orchestrator (this command's main loop) handles these — they are NOT delegated.

If a single phase appears to mix backend and frontend work, split it. Each phase must have exactly one owner.

## Step 3: Explore shared context

Before dispatching anything, the orchestrator reads:

- The plan file end-to-end.
- Every file listed under "Reference Files" in the plan.
- Every `docs/` page the plan cites — at minimum [../../docs/api/README.md](../../docs/api/README.md) when an endpoint is involved and the relevant page under [../../docs/business-rules/](../../docs/business-rules/).
- The rule files cited in the plan's "Applicable Rules, Skills & Agents" table — typically [../rules/backend_coding.md](../rules/backend_coding.md), [../rules/frontend_coding.md](../rules/frontend_coding.md), [../rules/security.md](../rules/security.md), [../rules/testing.md](../rules/testing.md), [../rules/upgrade-policy.md](../rules/upgrade-policy.md).

From this reading, build a **Cross-Module Contracts** note (in your working memory, not a file) containing the literal shapes that backend and frontend must agree on:

- The endpoint path and HTTP method.
- The Request DTO's exact field names and types.
- The Response DTO's exact field names and types.
- The error-key strings the backend will return and the frontend will branch on (must match [../../docs/api/README.md](../../docs/api/README.md) error-response-shape).
- Any new enum values, headers (`Idempotency-Key`, `Retry-After`), or query parameters.
- Any new Kafka event payload shape.

These shapes will be quoted verbatim into both agent prompts in Step 4. Do not paraphrase — paraphrasing causes drift.

## Step 4: Dispatch to developer agents

CRITICAL: when both `@backend-developer` and `@frontend-developer` have work to do, spawn them in a **single message with multiple `Agent` tool calls** so they run concurrently. Do not run them sequentially.

Each agent prompt MUST include, in this order:

1. **Plan context** — the full plan file path and a brief restatement of the task summary. Tell the agent: "Read the plan file before doing anything else."
2. **This agent's subset of tasks** — the specific Progress Tracker phases, files, and acceptance criteria assigned to this agent. List them explicitly, do not say "the backend half".
3. **Cross-module dependencies the agent must honour** — paste the Cross-Module Contracts note verbatim. Tell the agent: "These shapes are literal. Do not rename a field, do not re-case an `errorKey`, do not invent additional fields."
4. **Relevant rules and docs** — bullet list of `[path](path)` links to the rule files and `docs/` pages the agent must apply. At minimum:
   - Backend agent: [../rules/backend_coding.md](../rules/backend_coding.md), [../rules/security.md](../rules/security.md), [../rules/testing.md](../rules/testing.md), [../rules/upgrade-policy.md](../rules/upgrade-policy.md), [../../docs/api/README.md](../../docs/api/README.md), the relevant page under [../../docs/business-rules/](../../docs/business-rules/).
   - Frontend agent: [../rules/frontend_coding.md](../rules/frontend_coding.md), [../rules/security.md](../rules/security.md), [../rules/testing.md](../rules/testing.md), [../rules/upgrade-policy.md](../rules/upgrade-policy.md), [../../docs/api/README.md](../../docs/api/README.md), the same business-rules page.
5. **Skills to invoke** — the skills the agent should run, named exactly: `Skill("backend-create-rest-api")`, `Skill("backend-create-unit-test")`, `Skill("frontend-implement-ui-component")` as appropriate per the plan.
6. **Verification command** — what the agent must run before reporting back:
   - Backend: invoke `Skill("backend-verify")`.
   - Frontend: invoke `Skill("frontend-verify")`.
7. **"DO NOT create git commits"** — verbatim. Tell the agent the orchestrator handles commit decisions, and the user owns merge requests.

Set each agent's `subagent_type` to its specialist name when dispatching (`backend-developer` / `frontend-developer`). The names are final and will be created in step 6 of the bootstrap.

## Step 5: Handle cross-module tasks

The orchestrator (this command) directly executes phases that are neither pure backend nor pure frontend:

- Edits under [../../docs/](../../docs/) — API contract updates, business-rule additions, ADR drafting, plan archive moves.
- Root config — `pom.xml`, `package.json`, `pnpm-workspace.yaml`, `docker-compose.yml`, `application.properties` at the project root.
- Shared contract scaffolding when the plan calls it out (e.g. agreeing on an `errorKey` string in [../../docs/api/README.md](../../docs/api/README.md) before either agent writes the implementation).
- Wiring that requires sequencing both sides (e.g. confirming the frontend Vue Query path string matches the backend `@Path` constant after both agents finish).

Do this work before, between, or after the agent dispatch as the phase ordering demands. If the plan's Progress Tracker shows a cross-module phase before the agents can start, complete it first.

## Step 6: Verify the combined result

After all agents have returned and all cross-module phases are done, run the verification skills in this order:

1. If any backend file was touched: `Skill("backend-verify")`.
2. If any frontend file was touched: `Skill("frontend-verify")`.
3. Always: `Skill("code-review")` — applied to the diff against the base branch.

Failure handling:

- If `backend-verify` fails, route the specific error excerpt back to `@backend-developer` with: the failing phase, the exact error message, the file and line, and the rule citation if applicable. Do not paraphrase the error — paste the verifier output.
- Same pattern for `frontend-verify` and `@frontend-developer`.
- If `code-review` reports `block` findings, route each finding to the responsible agent (backend or frontend). `warn` findings are reported in Step 7 but do not block.

Re-run the relevant verifier after the agent reports a fix. Do not declare success until all three verifiers pass (or `code-review` is at most `warn` / `info`).

## Step 7: Report

Print a "## Implementation Report" with the following sections, in order:

- **Plan** — the plan file path.
- **Title** — the task summary from the plan header.
- **Execution mode** — `parallel` (two agents dispatched concurrently), `backend-only`, `frontend-only`, or `orchestrator-only`.
- **Agent Results** — one block per agent:
  - Phases completed (checked from the Progress Tracker).
  - Files touched (path + Create / Modify / Delete).
  - Verification result (`PASS` / `FAIL` with the excerpt if failed).
- **Cross-Module Tasks** — phases the orchestrator executed directly, with files touched.
- **Verification Results** — a table:

  | Step | Result | Notes |
  |---|---|---|
  | `backend-verify` | PASS / FAIL / SKIPPED | … |
  | `frontend-verify` | PASS / FAIL / SKIPPED | … |
  | `code-review` | PASS / WARN / BLOCK | summary |

- **Acceptance Criteria** — re-list the plan's checklist with each item marked `[x]` (verified pass) or `[ ]` (still unmet) with a one-line reason.
- **Issues Encountered** — anything that went wrong during execution and how it was resolved. Empty if everything was clean.
- **Next Steps** — typically: "Review the diff and run `Skill("create-merge-request")` when ready." Do NOT auto-open a PR.

## Important rules

- **DO NOT** create git commits during execution. The user decides when to commit.
- **DO** spawn `@backend-developer` and `@frontend-developer` in parallel — one message, multiple `Agent` tool calls — whenever both have work in the plan.
- **DO** give each agent the **full plan** (file path + brief summary). Don't trim the plan to "their bit" — they need the broader context to honour cross-module contracts.
- **DO** include the literal cross-module contract shapes in BOTH agent prompts. Paraphrasing causes drift.
- **DO** invoke `Skill("code-review")` at the end, every time. Even if both verifiers passed.
- **DO** implement the plan fully in one execution pass. The user has already approved the plan; do NOT pause between phases for re-approval.
- **DO NOT** skip a verify step because "it looks fine" — verifiers exist precisely because eyeballing is insufficient.
- **DO NOT** modify the plan file during execution. If the plan is wrong, stop, report the gap, and tell the user to rerun `/make-plan`. The plan is an input artifact, not a working document.
- **DO NOT** read or write outside the directories named in the plan's File Structure section, except for the verifier skills' own scope and `docs/` updates the plan explicitly calls out.

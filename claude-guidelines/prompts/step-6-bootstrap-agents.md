# Step 6 — Bootstrap `.claude/agents/`

**When to run:** after step 5 — commands must exist (they reference these agents).
**Output:** `.claude/agents/` directory with 1–3 specialist agent files.
**Estimated time:** 15–25 minutes.
**Prerequisites:** all previous artifacts committed.

---

## What this step produces

Agents are **specialist personas** with their own system prompt and a fixed tool set. The main Claude session can delegate to them via the `Agent` tool. Each lives as `.claude/agents/<agent-name>.md`.

Target baseline agents (conditional rows skip when not applicable):

```
.claude/agents/
├── backend-developer.md       # if §4.1 backend exists
└── frontend-developer.md      # if §4.2 frontend exists
```

Why so few? Agents are coarse — one per major "developer role" in the project. Skills do the fine-grained work; agents wrap them with persona and context. Adding more agents later (e.g. `db-administrator`, `security-reviewer`) is normal; the bootstrap establishes the developer baseline.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are bootstrapping `.claude/agents/` for this repository.

INPUT:
- Read `CLAUDE.md`, the `docs/` tree, `.claude/rules/`, `.claude/skills/`, and `.claude/commands/`.
- Read `claude-guidelines/reference/what-each-artifact-is-for.md` if present.

GOAL:
Produce one specialist agent per major developer role in the project (typically backend and frontend). Each agent:
- has a clear "when to use / when NOT to use" boundary so the main session can route correctly
- prefers invoking skills (from step 4) over hand-writing the same work
- knows the project's rules, docs, and tech stack cold

TARGET FILES (omit conditional ones based on `project-info.md`):

.claude/agents/
├── backend-developer.md      (§4.1 backend exists)
└── frontend-developer.md     (§4.2 frontend exists)

WRITING CONVENTIONS:
- Each agent file starts with YAML frontmatter:
  ---
  name: <agent-slug>
  description: >
    <one-paragraph description of the agent's expertise, when to use it (5+ example user phrasings),
    and explicit "Do NOT use for X" exclusions.>
  tools: Read, Write, Edit, Glob, Grep, Bash, Agent
  model: opus | sonnet | haiku  (pick based on complexity — backend-dev = opus, simple agents = sonnet)
  ---
- After frontmatter: a `You are a **<role>** specializing in <project>...` opening that establishes persona.
- A "Note on repo state" callout if the codebase isn't scaffolded yet.
- Mandatory sections (in order):
  1. **Your Tech Stack** — table pulled from `project-info.md §4`.
  2. **Before Writing Any Code** — numbered list: read rules, fact-check against docs/, read existing code if any, understand the domain.
  3. **Project Structure** — ASCII tree from `docs/architecture/`.
  4. **Leveraging Skills** — table of (task → skill invocation → what it does). State the rule: "Always prefer skill invocation over ad-hoc work."
  5. **Implementation Workflow** — numbered: Understand, Plan, Implement (bottom-up for backend / top-down for frontend), Verify (invoke `*-verify` skill), Self-review (invoke `code-review` skill).
  6. **Self-Review Checklist** — bulleted list, each item tagged to the rule section that defines it.
  7. **Key Patterns You Must Follow** — code snippets in the project's language showing canonical: API resource, service impl, repository, exception+mapper, unit test (for backend); component, template, service, spec (for frontend). Each snippet cites the rule section it implements.
  8. **What NOT to Do** — bulleted forbidden list, each tied to a rule section.

PER-AGENT CONTENT:

### backend-developer.md
- Persona: senior <stack> backend engineer (e.g. "senior Quarkus backend engineer") with 10+ years' experience.
- Tech stack: full backend table from §4.1, §4.3, §4.4, §4.5.
- Skills table at minimum:
  | Task | Skill |
  |---|---|
  | Scaffold a new resource end-to-end | `Skill("backend-create-rest-api")` |
  | Generate a unit test for a class | `Skill("backend-create-unit-test")` |
  | Verify | `Skill("backend-verify")` |
  | Review the diff | `Skill("code-review")` |
  | Open a PR | `Skill("create-merge-request")` |
- Implementation workflow: bottom-up — migration → entity → repository → DTOs → service → resource → test.
- Self-review checklist: cite `backend_coding.md`, `security.md`, `testing.md` sections.
- Key patterns: include canonical code snippets for an API resource, a service implementation with the project's signature patterns (idempotency, locking, transaction boundary), a repository with the locking helper, a domain exception + mapper, and a JUnit/Mockito test scaffold.
- What NOT to do: pull from the rule files' "MUST NOT" and "Never" statements. Aim for 10–15 specific don'ts.

### frontend-developer.md (conditional on §4.2)
- Persona: senior <framework> engineer.
- Tech stack: full frontend table from §4.2, §4.5.
- Skills table at minimum:
  | Task | Skill |
  |---|---|
  | Scaffold a component | `Skill("frontend-implement-ui-component")` |
  | Verify | `Skill("frontend-verify")` |
  | Review the diff | `Skill("code-review")` |
  | Open a PR | `Skill("create-merge-request")` |
- Implementation workflow: top-down — route → page component → presentational children → feature service → spec. Add `data-test` attributes as you go.
- Self-review checklist: cite `frontend_coding.md`, `security.md`, `testing.md §3` sections.
- Key patterns: include canonical snippets for a standalone component with signals (or React functional component), a Reactive Form, a service with HTTPClient, a spec with render test and XSS regression. Each cites the rule section.
- What NOT to do: pull from `frontend_coding.md §19` (Anti-patterns).

CRITICAL CONSISTENCY CHECKS:
- The `tools:` line in frontmatter MUST include `Agent` so the agent can delegate further (e.g. invoke skills via the Skill mechanism, or spawn Explore sub-agents).
- The "Do NOT use for X" clause in the description must explicitly exclude the OTHER agent's domain (backend agent says "Do NOT use for frontend TypeScript, …"; frontend agent says "Do NOT use for backend Java, …").
- Every Skill invocation referenced must actually exist in `.claude/skills/`.
- Every rule section cited must exist in `.claude/rules/`.

PROCESS:
1. Write each agent file.
2. Verify Skill and rule citations resolve.

OUTPUT:
1. Print created file paths.
2. Print a "Skill citation check" — list every `Skill("...")` per agent; confirm each exists.
3. Print a "Rule citation check" — list every rule-section reference; flag any that don't resolve.
4. Print a "Routing clarity check" — quote the "when to use" and "when NOT to use" lines from each agent; flag any overlaps that could cause the main session to route incorrectly.

DO NOT:
- Modify files outside `.claude/agents/`.
- Invent skills that weren't created in step 4.
- Use emojis.
- Create a git commit.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| Each agent file has frontmatter with `name`, `description`, `tools`, `model` | `head -10 .claude/agents/*.md` |
| `tools:` includes `Agent` so agents can delegate further | grep for `tools:` in each. |
| Skill citations resolve | Read the report's "Skill citation check". |
| Rule citations resolve | Read the report's "Rule citation check". |
| Backend and frontend agents don't have overlapping "when to use" triggers | Read the "Routing clarity check". |
| Each agent has a self-review checklist with rule citations | Open both; verify. |
| Key Patterns snippets are in the project's actual languages (Java vs. JS, etc.) | Open both; verify language fence syntax. |

If any check fails, rerun in a fresh session.

---

## Commit

```bash
git add .claude/agents/
git commit -m "claude: add specialist agents"
```

Then move to [step-7-validate-and-iterate.md](step-7-validate-and-iterate.md).

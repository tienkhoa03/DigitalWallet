# What Each Artifact Is For

A five-minute mental model. Read once before the first bootstrap; refer back when you're not sure where new content belongs.

---

## The whole stack on one page

| Artifact | Lives at | Loaded by Claude when… | Owns | Cites |
|---|---|---|---|---|
| **`CLAUDE.md`** | repo root | every session, automatically | the "what is this project" briefing — stack, architecture, invariants | `docs/`, `.claude/rules/` |
| **`docs/`** | repo root | on demand (when relevant) | the *what* and *why* — product behaviour, architecture, ADRs, domain, business rules | external specs |
| **`.claude/rules/`** | `.claude/` | on demand (when changing code) | the *how* — coding standards, security floor, testing policy, dependency baselines | `docs/`, other rule files |
| **`.claude/skills/`** | `.claude/` | when Claude routes to a skill | repeatable procedures (scaffold, verify, review, PR) | `.claude/rules/` |
| **`.claude/commands/`** | `.claude/` | when user types `/command-name` | user-invokable workflows that compose skills | `.claude/skills/`, `.claude/agents/`, `.claude/rules/` |
| **`.claude/agents/`** | `.claude/` | when main session delegates with the `Agent` tool | specialist personas with fixed tool sets | `.claude/skills/`, `.claude/rules/`, `docs/` |
| **`project-info.md`** | repo root (temporary) | only by bootstrap prompts | the input template — distilled product spec | external specs |

---

## The two critical separations

### `docs/` vs. `.claude/rules/`

These are **the most-confused pair** in the stack. Internalise the distinction:

| Question | Answer | Lives in |
|---|---|---|
| What does this product do? | Product behaviour, user journeys, FRs | `docs/` |
| Why does the system look this way? | Architectural decisions, ADRs, trade-offs | `docs/decisions/` |
| What rule does the running app enforce? | Business rules ("balance must be ≥ 0") | `docs/business-rules/` |
| How do I write a JAX-RS resource? | Coding pattern, code snippet | `.claude/rules/backend_coding.md` |
| What goes in DTO naming? | Coding convention | `.claude/rules/backend_coding.md` |
| When can I write `console.log`? | Coding floor | `.claude/rules/security.md`, `.claude/rules/frontend_coding.md` |

**Test:** if the content describes a property a *human user of the product* would care about → `docs/`. If it describes how a *contributor* writes code → `.claude/rules/`.

**They cross-link, they never duplicate.** A rule that says "all transfer rejections return 409 with `INSUFFICIENT_FUNDS`" duplicates a business rule and drifts. Replace it with: "see [docs/business-rules/transfer-rules.md](…)".

### Skill vs. Command vs. Agent

| Concept | Granularity | Invoked by | Has its own tool set? | Has its own persona? |
|---|---|---|---|---|
| **Skill** | one procedure | Claude (auto), commands, agents | no — inherits caller's | no — uses the procedure verbatim |
| **Command** | one user workflow | the user typing `/name` | no — runs in main session | no — runs in main session |
| **Agent** | one specialist role | main session via `Agent` tool | yes — declared in frontmatter | yes — own system prompt |

**Test:** if it's a parameterised procedure → skill. If it's "the user triggers it with a slash" → command. If it's "delegate this whole task to a specialist who works in isolation" → agent.

A command can invoke skills and agents. An agent can invoke skills. A skill should generally not invoke commands (commands are the user's entry point, skills are internal).

---

## Where new content goes — decision tree

```
You have something to write down. Where does it go?

│
├── Is it a fact about what the product DOES or WHY it was built that way?
│   └── docs/
│       ├── business-rules/   if it's a runtime invariant
│       ├── architecture/      if it's a structural fact
│       ├── decisions/         if it captures a decision with trade-offs
│       ├── domain-knowledge/  if it's a definition or user journey
│       ├── api/               if it's an endpoint contract
│       └── database/          if it's a schema / migration policy
│
├── Is it a CODING RULE (how to write code in this repo)?
│   └── .claude/rules/
│       ├── backend_coding.md      structure, frameworks, patterns
│       ├── frontend_coding.md     component, state, forms
│       ├── security.md            cross-cutting security floor
│       ├── testing.md             test policy, coverage, mocking matrix
│       └── upgrade-policy.md      version baselines, new-code guardrails
│
├── Is it a REPEATABLE PROCEDURE you'll run again?
│   └── .claude/skills/<slug>/SKILL.md
│       (parameterised; cited by the user, commands, agents)
│
├── Is it a USER-INVOKED WORKFLOW you'll trigger with /command?
│   └── .claude/commands/<slug>.md
│       (often composes multiple skills)
│
├── Is it a SPECIALIST PERSONA you delegate whole tasks to?
│   └── .claude/agents/<role>.md
│       (frontmatter: name, description, tools, model)
│
├── Is it a TOP-LEVEL BRIEFING all sessions need to know?
│   └── /CLAUDE.md
│       (terse — < 300 lines; cite, don't restate)
│
└── Is it a piece of ephemeral plan, in-progress thinking, or task scratchpad?
    └── docs/plans/implementation-plan-<slug>.md   (the /make-plan command writes these)
        OR — don't write it at all. Conversation context is fine.
```

---

## Anti-patterns

| Anti-pattern | Why it bites | Fix |
|---|---|---|
| Business rule duplicated in `docs/business-rules/` and `.claude/rules/backend_coding.md` | Two sources of truth → drift | Keep it in `docs/business-rules/`; have `backend_coding.md` cite it. |
| `CLAUDE.md` grew to 800 lines with full domain glossary inline | Burns context on every interaction | Move detail to `docs/`; `CLAUDE.md` cites it. |
| Agent prompt re-states all the coding rules inline | Drifts the moment rules change | Agent reads `.claude/rules/` at runtime — say "read [.claude/rules/backend_coding.md](…) first". |
| Skill has its own coding conventions baked in | Conflicts with rules silently | Skill cites rules by section; user updates rules once, skill stays correct. |
| Two skills with overlapping trigger phrases | Claude routes inconsistently | Tighten each description; make exclusions explicit. |
| Rule file says "we MAY do X" | Not enforceable | Rules are MUST / MUST NOT / Prefer / Avoid. Anything weaker belongs in a docs/decisions ADR or design note. |
| ADR written *after* implementation lands | Loses the decision context | Draft as Proposed before coding; mark Accepted when the PR merges. |
| `docs/plans/<plan>.md` referenced after the work is done | Plans rot; people read them as truth | Plans are inputs to `/implement-plan`. Once the work merges, delete the plan or archive under `docs/plans/_archive/`. |

---

## Greenfield vs. existing-code differences

**Greenfield** (no source code yet):
- Every claim about a file path or behaviour is suffixed `(verify)` or `(spec — not yet implemented)`.
- Skills detect-and-skip when the project isn't scaffolded (e.g. `backend-verify` exits with "PASS (skipped) — backend module not yet scaffolded" instead of running `mvn`).
- Code snippets in rules and agents are **canonical examples**, not "this is what the codebase already does".

**Retrofitting existing code**:
- Replace `(verify)` markers with file paths once you've confirmed them.
- Skills run against the actual project.
- Code snippets in rules should **match a real example file** — cite the file rather than fabricating.

The bootstrap defaults to greenfield. After the first real code lands, do a sweep through `docs/` and `.claude/rules/` removing `(verify)` markers as you go.

---

## A worked example: "I want to add a Slack integration"

Where does each piece of writing land?

| Content | Destination |
|---|---|
| "We integrate with Slack to post fraud alerts" (the decision and why) | `docs/decisions/000N-slack-integration.md` (ADR) |
| "Slack messages use Block Kit with action buttons" (the contract) | `docs/architecture/README.md §<integrations>` or a new `docs/integrations/slack.md` |
| "Use `quarkus-slack-client` extension" (the new dependency) | `.claude/rules/upgrade-policy.md §1` (baseline table) |
| "Slack tokens stored in env var `SLACK_BOT_TOKEN`, never logged" | `.claude/rules/security.md §1.4`, also `docs/architecture/README.md §7` (env-var table) |
| "How to scaffold a new Slack publisher" (procedure) | `.claude/skills/slack-create-publisher/SKILL.md` |
| (optional) "Slack-integrations specialist persona" | `.claude/agents/slack-integration-developer.md` — usually overkill; the backend-developer can handle it via the skill |

Notice: most of the volume lands in `docs/`. Rules get a few additions. Skills get one new procedure. No new agent needed.

---

## Further reading

- [README.md](../README.md) — the playbook overview
- [03-initialization-workflow.md](../03-initialization-workflow.md) — the step-by-step
- [file-naming-conventions.md](file-naming-conventions.md) — slugs, kebab-case, anchors

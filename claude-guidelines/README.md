# Claude-First Project Initialization Guidelines

A reusable playbook for bootstrapping any new software project so that Claude Code (and other AI assistants) can collaborate on it productively **from day one** — before a single line of source code is written.

The output of this playbook, applied to a brand-new repo, is a coherent set of:

- **`CLAUDE.md`** — the AI's top-of-context briefing (stack, architecture, invariants, commands).
- **`docs/`** — the **single source of truth** about *what* the system is and *why* (business rules, architecture, ADRs, domain knowledge, API contracts, database schema).
- **`.claude/rules/`** — *how* to write code in this repo (coding standards, security floor, testing policy, dependency baselines). The enforcement layer.
- **`.claude/skills/`** — repeatable, parameterized procedures (scaffold a REST API, run a verify pipeline, open a merge request).
- **`.claude/commands/`** — user-invokable workflows (`/make-plan`, `/implement-plan`).
- **`.claude/agents/`** — specialized sub-agents that own a domain (backend developer, frontend developer).

The playbook is **stack-agnostic**. The reference examples in this folder cite the Digital Wallet (Quarkus + Angular) project that ships with these guidelines, but every prompt and template is parameterised by a single project-info file you fill in once.

---

## How the artifacts relate

```
                        ┌───────────────────────────┐
                        │  01-project-info.md       │  ← you fill this once
                        │  (the input template)     │
                        └─────────────┬─────────────┘
                                      │
              ┌───────────────────────┴────────────────────────┐
              ▼                                                ▼
   ┌─────────────────────┐                          ┌────────────────────┐
   │ CLAUDE.md           │  top-of-context briefing │  docs/             │
   │ (always loaded)     │←────── cross-link ──────→│  source of truth   │
   └─────────────────────┘                          └─────────┬──────────┘
              ▲                                               │
              │ cite                                          │ cite
              │                                               ▼
   ┌──────────┴──────────────────────────────────────────────────────────┐
   │  .claude/rules/   (HOW to code — coding standards, security,        │
   │                    testing, upgrade policy)                          │
   └──────────────────────────────────────────────────────────────────────┘
              ▲                ▲                    ▲
              │ enforced by    │ enforced by        │ enforced by
              │                │                    │
   ┌──────────┴────────┐  ┌────┴───────────────┐  ┌─┴───────────────────┐
   │ .claude/skills/   │  │ .claude/commands/   │  │ .claude/agents/      │
   │ (repeatable       │  │ (user workflows:    │  │ (domain specialists  │
   │  procedures)      │  │  /make-plan, etc.)  │  │  e.g. backend-dev)   │
   └───────────────────┘  └─────────────────────┘  └──────────────────────┘
```

**Two crisp distinctions** worth memorising before you start:

| Pair | Distinction |
|---|---|
| `docs/` vs. `.claude/rules/` | `docs/` describes **what** the system is and **why**; `.claude/rules/` describes **how** to code inside it. They cross-link, they don't duplicate. |
| Skills vs. agents vs. commands | A **skill** is a procedure (parameterised by inputs). A **command** is a user-invoked entry point (`/make-plan`). An **agent** is a persona with its own tool set and system prompt. A command can invoke skills; an agent can invoke skills. |

See [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md) for a deeper mental model.

---

## The workflow at a glance

You will go through **seven steps**, in order, from a completely empty repo to a Claude-ready project.

| Step | Output | Prompt to use | When |
|---|---|---|---|
| 0 | Fill the project-info template | — | Before any Claude prompts. |
| 1 | `CLAUDE.md` (root) | [prompts/step-1-bootstrap-claude-md.md](prompts/step-1-bootstrap-claude-md.md) | First. Everything downstream cites it. |
| 2 | `docs/` source-of-truth tree | [prompts/step-2-bootstrap-docs.md](prompts/step-2-bootstrap-docs.md) | Right after CLAUDE.md. |
| 3 | `.claude/rules/` coding standards | [prompts/step-3-bootstrap-rules.md](prompts/step-3-bootstrap-rules.md) | After docs — rules cite docs. |
| 4 | `.claude/skills/` procedures | [prompts/step-4-bootstrap-skills.md](prompts/step-4-bootstrap-skills.md) | After rules — skills cite rules. |
| 5 | `.claude/commands/` workflows | [prompts/step-5-bootstrap-commands.md](prompts/step-5-bootstrap-commands.md) | After skills — commands compose skills. |
| 6 | `.claude/agents/` domain experts | [prompts/step-6-bootstrap-agents.md](prompts/step-6-bootstrap-agents.md) | After everything else — agents reference all of it. |
| 7 | Validation pass (cross-refs, gaps) | [prompts/step-7-validate-and-iterate.md](prompts/step-7-validate-and-iterate.md) | Final. |

Each prompt is a standalone, copy-paste block. Open a fresh Claude session, paste, and let it work. The prompts assume the previous step's output exists.

---

## Folder map

| Path | What's there |
|---|---|
| [01-project-info-template.md](01-project-info-template.md) | **The fillable template.** Copy it to your repo root as `project-info.md` and fill it in. |
| [02-how-to-fill-template.md](02-how-to-fill-template.md) | Field-by-field instructions for filling the template, with do/don't examples. |
| [03-initialization-workflow.md](03-initialization-workflow.md) | The full end-to-end workflow — prerequisites, ordering, what to commit when, how to iterate later. |
| [04-ongoing-development-workflow.md](04-ongoing-development-workflow.md) | What to do **after** the bootstrap: day-to-day plan→implement→verify→review→ship loop, plus the propagation flow for when `project-info.md` changes. |
| [prompts/](prompts/) | Two families: `step-N-*.md` for sequential bootstrap (run once, in order), `ongoing-*.md` for event-driven procedures (run when triggered — spec change, marker sweep, health check, new artefact). Copy and paste into a Claude Code session. |
| [reference/](reference/) | Mental models and conventions. Read once; refer back when an edge case shows up. |
| [examples/](examples/) | A worked example based on the Digital Wallet project (this repo's own `docs/`, `.claude/rules/`, etc. are the live reference). |

---

## When to use this playbook

- **Greenfield project.** New repo, no code yet. Ideal — every artifact lands consistently.
- **Existing project, no `.claude/`.** Works, but you'll be retro-fitting. The `docs/` step is heavier because you must extract truth from existing code instead of from a spec.
- **Existing project with `.claude/`.** Don't re-run from scratch. Use the playbook as a checklist — find the gaps (e.g., "we have rules but no agents") and fill them in piecemeal.

---

## Prerequisites

- Claude Code installed and authenticated.
- An empty (or near-empty) git repository.
- A clear product spec or PRD you can paraphrase into the template. If you only have a vague idea, write the spec first — Claude cannot read your mind.
- ~1 hour of focused time per step. Steps 2 and 3 are the longest.

---

## How to use these files

1. Read this `README.md` (you're here).
2. Read [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md) — five-minute mental model.
3. Read [03-initialization-workflow.md](03-initialization-workflow.md) end-to-end before starting.
4. Copy [01-project-info-template.md](01-project-info-template.md) to your repo root as `project-info.md` and fill it (use [02-how-to-fill-template.md](02-how-to-fill-template.md) as the field guide).
5. Run the seven prompts in order, committing after each.
6. Delete or archive `project-info.md` once `CLAUDE.md` + `docs/` reflect its contents — the template is scaffolding, not source of truth.

---

## Design principles (why this playbook is shaped the way it is)

- **One source of truth per concept.** `docs/` owns *what & why*; `.claude/rules/` owns *how*. No content lives in both. They cross-link.
- **Cite, don't paraphrase.** A rule that says "see backend §4.4" stays consistent when the rule changes. A rule that re-states the policy drifts.
- **Skills before agents.** Agents are wrappers; the actual procedures live in skills so they can be invoked from anywhere (commands, agents, slash commands, even the main loop).
- **Bottom-up bootstrapping.** `docs/` → `rules/` → `skills/` → `commands/` → `agents/`. Each layer references the layer below; reversing the order forces rework.
- **Project-agnostic prompts.** Every prompt reads `project-info.md` and adapts. Swapping stacks is "edit the template, re-run the prompts".
- **No greenfield assumptions.** Prompts handle "the module isn't scaffolded yet" gracefully — they emit `(verify)` markers rather than inventing fictional file paths.

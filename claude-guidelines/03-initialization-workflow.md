# Initialization Workflow

End-to-end procedure for bootstrapping a Claude-ready project from an empty repository. Follow it once per project.

> Estimated time: **3–5 hours of focused work**, split across the seven steps. Step 2 (`docs/`) and Step 3 (`.claude/rules/`) are the longest because every later artifact cites them.

---

## Prerequisites

Before running the first prompt, confirm:

| Check | How to verify |
|---|---|
| Claude Code installed and authenticated | `claude --version` runs; `claude doctor` reports OK. |
| Empty (or near-empty) git repo initialised | `git status` shows clean tree with at most a `README.md` / `.gitignore`. |
| `project-info.md` filled at repo root | Copy of [01-project-info-template.md](01-project-info-template.md), every section answered per [02-how-to-fill-template.md](02-how-to-fill-template.md). |
| §16 in `project-info.md` has zero `❓ Unanswered` *and* `Blocking` rows | Open questions are either answered or marked `⏳ Deferred`. |
| You have ~3–5 hours of focused time | Splitting steps 2–3 across days causes context drift. |

If any check fails, fix it first. The downstream cost of bootstrapping on a vague `project-info.md` is rework, not a faster start.

---

## Workflow at a glance

```
┌──────────────────────────────────────────────────────────────────┐
│ Step 0: Fill project-info.md  (no Claude — you write it)         │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 1: Bootstrap CLAUDE.md   (prompts/step-1-bootstrap-claude-md)│
│   Output: /CLAUDE.md                                              │
│   Commit:  "docs: add CLAUDE.md briefing"                         │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 2: Bootstrap docs/       (prompts/step-2-bootstrap-docs)     │
│   Output: /docs/** (architecture, business-rules, api, db, ADRs,  │
│           domain-knowledge, testing, onboarding)                  │
│   Commit:  "docs: add source-of-truth tree"                       │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 3: Bootstrap .claude/rules/  (prompts/step-3-bootstrap-rules)│
│   Output: backend_coding.md, frontend_coding.md (if FE), security │
│           testing.md, upgrade-policy.md                           │
│   Commit:  "claude: add coding rules"                             │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 4: Bootstrap .claude/skills/ (prompts/step-4-bootstrap-skills)│
│   Output: backend-create-rest-api, backend-create-unit-test,      │
│           backend-verify, frontend-implement-ui-component (if FE),│
│           frontend-verify (if FE), code-review, create-merge-     │
│           request                                                 │
│   Commit:  "claude: add skills"                                   │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 5: Bootstrap .claude/commands/ (prompts/step-5-…)            │
│   Output: make-plan, implement-plan                               │
│   Commit:  "claude: add slash commands"                           │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 6: Bootstrap .claude/agents/  (prompts/step-6-…)             │
│   Output: backend-developer, frontend-developer (if FE)           │
│   Commit:  "claude: add specialist agents"                        │
└────────────────────────────────────┬─────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│ Step 7: Validation pass            (prompts/step-7-…)             │
│   Output: gap-report.md or fixes-applied                          │
│   Commit:  "claude: validation fixes"                             │
└──────────────────────────────────────────────────────────────────┘
```

---

## Running each step

The procedure for every step is identical:

1. **Open a fresh Claude Code session.** A clean context window prevents leakage from earlier steps.
2. **Open the prompt file** (`prompts/step-N-…md`) and copy its body verbatim into the Claude input.
3. **Let Claude run.** Each prompt produces files and prints a short summary. Do not interrupt mid-step.
4. **Skim the output.** Glance at the diff. Don't accept silently — see "How to review each step's output" below.
5. **Commit.** Use the suggested commit message from the diagram above (or your team's convention).
6. **Move to the next step.**

> **One step per session.** Do not chain steps in a single session. Each prompt assumes a fresh start so that Claude re-reads the artifacts produced by the previous step instead of relying on stale memory.

---

## How to review each step's output

For each step, ask three questions:

1. **Does it match `project-info.md`?** Stack names, NFR numbers, module paths, glossary terms — pick three at random and verify they round-trip.
2. **Are cross-references real?** Every "see [.claude/rules/backend_coding.md §4](…)" should resolve to a section that exists. Click two or three.
3. **Are placeholders flagged or invented?** Greenfield projects will have many `(verify)` and `(spec — not yet implemented)` markers. That's correct. **Invented file paths** ("see `src/main/java/com/acme/wallet/api/TransferResource.java`") that don't exist yet are *also* OK as long as they're marked `(spec)`. **Quietly invented behaviours** ("the system uses JWT with HS256") are not OK.

A rough pass/fail gauge:

| Symptom | Verdict |
|---|---|
| Output reads like `project-info.md` expanded with detail | PASS |
| Output references files that don't exist, without `(verify)` markers | FAIL — rerun with the diff in the prompt |
| Output contradicts an NFR | FAIL — fix the NFR first if you actually want the contradiction, else rerun |
| Output omits an Epic / FR / domain term | PARTIAL — add it manually or rerun the step with a "you missed FR3.X" patch |

---

## Commit hygiene

Commit after **each step**. Do not bundle steps into one mega-commit. Reasons:

- If step 3 produces bad rules and step 4 cites them, rolling back step 3 alone is easier with separate commits.
- A reviewer (human or AI) can read each layer in isolation.
- The PR description writes itself: "step 1: CLAUDE.md; step 2: docs/; step 3: rules; …"

Suggested commit messages are listed in the workflow diagram. They follow Conventional Commits format if your project uses it; adapt to your team's style.

---

## What to do if a step goes off the rails

Three common failure modes and their fixes:

### "Claude generated something that doesn't match my spec"

Cause: the relevant section of `project-info.md` was ambiguous, or Claude pulled from training data.

Fix:

1. Identify the offending section in the output.
2. Tighten the corresponding section of `project-info.md`.
3. Rerun the same prompt in a **fresh** session.

Do not patch the output by hand and move on — the same gap will reappear in later steps.

### "Two steps' outputs disagree"

Cause: `project-info.md` changed between sessions, *or* the later step has more context than the earlier one.

Fix:

1. Re-read `project-info.md`. Is it still the same?
2. Identify which output is more correct.
3. Re-run the **earlier** step in a fresh session. The later step will likely fall into line on the next regeneration; if not, re-run it too.

### "I want to add a rule / skill / agent that isn't in the prompts"

That's fine — the prompts produce a baseline. Adding more is the normal evolution.

Procedure:

1. **Don't edit the prompts to expand them.** Edit them only if the *bootstrap baseline* should change for future projects.
2. Use the relevant `.claude/skills/<existing-skill>/SKILL.md` or `.claude/agents/<existing-agent>.md` as the **template**, copy and adapt.
3. Cross-link from the index files (`MEMORY.md` if applicable, plus any "see also" rows in `.claude/skills/README.md`).

See [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md) for a decision tree on where new content belongs.

---

## After step 7

You now have:

- A `CLAUDE.md` that loads automatically into every Claude session.
- A `docs/` tree that humans and AI both treat as the source of truth.
- A `.claude/` directory with rules, skills, commands, and agents that compose into a productive workflow.

**Next actions** (not part of the bootstrap):

1. Run `/make-plan` (the command you just created) on your first real feature — verify the workflow end-to-end.
2. Tag this point in git: `git tag -a v0.0.0-claude-bootstrap -m "Claude-ready baseline"`.
3. Open a "first PR" using `Skill("create-merge-request")` — confirms the merge-request skill is wired correctly.
4. Archive or delete `project-info.md`. Its content is now in `CLAUDE.md` and `docs/`.

---

## Iterating later

The bootstrap creates a baseline, not a frozen artifact. As the project evolves:

| Trigger | Action |
|---|---|
| New tech stack component (e.g. add Redis after MVP) | Update `project-info.md §4`, then update `docs/architecture/§2`, `.claude/rules/upgrade-policy.md` baseline table, and any rule that references the stack. |
| Major architectural decision | Write a new ADR in `docs/decisions/000N-<slug>.md`. Cite the ADR from the relevant rule. |
| New repeatable procedure (e.g. "generate a Kafka consumer") | Add a new skill under `.claude/skills/<skill-name>/SKILL.md` using an existing one as a template. |
| New specialist domain | Add a new agent under `.claude/agents/<role>-developer.md`. |
| Rule violation found in a PR | Either tighten the rule (preferred — codify the lesson) or document the exception in an ADR. |

Re-running the full bootstrap is **not** the right answer for incremental change. Use it only when the project undergoes a foundational rewrite (e.g. swap Quarkus for Spring Boot — but at that point, the project is essentially new).

For the **day-to-day** AI workflow after this bootstrap (plan → implement → verify → review → ship) and the **propagation procedure** when `project-info.md` changes, see [04-ongoing-development-workflow.md](04-ongoing-development-workflow.md).

---

## Frequently asked questions

**Can I skip docs/ and go straight to rules?**
No. Rules cite `docs/` constantly ("see [docs/business-rules/transfer-rules.md](…)"). Without docs, every rule becomes either vague or duplicative.

**Can I skip the PR/MR skill if I'm a solo developer?**
Yes. Delete `.claude/skills/create-merge-request/` after step 4 — nothing else depends on it.

**Can I run two steps in parallel?**
No. Each prompt's output is input to the next. Parallelism corrupts the chain.

**My project has no frontend — do I still get a frontend agent / rules?**
No. The prompts detect `§4.2: N/A — backend-only` and skip frontend artefacts entirely.

**Can I use this for a non-software project (e.g. a research repo)?**
The template assumes a software project. For data science / research repos, fork the template, simplify §3–§4, and keep §5–§6 + §15. The prompts will need lighter editing.

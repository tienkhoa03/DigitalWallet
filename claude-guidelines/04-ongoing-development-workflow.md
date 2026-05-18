# Ongoing Development Workflow

What to do **after** the seven bootstrap steps have shipped a Claude-ready baseline. This file is the playbook for everyday feature work, for keeping the AI artefacts in sync with reality, and for the round-trip that fires whenever [project-info.md](../project-info.md) changes.

> Prerequisite: [03-initialization-workflow.md](03-initialization-workflow.md) has been run end-to-end. `CLAUDE.md`, [docs/](../docs/), [.claude/rules/](../.claude/rules/), [.claude/skills/](../.claude/skills/), [.claude/commands/](../.claude/commands/), and [.claude/agents/](../.claude/agents/) exist and are committed.

---

## TL;DR

| Situation | Entry point |
|---|---|
| You have a new feature to build | `/make-plan` → review the plan file → `/implement-plan` |
| You want a single API or component scaffolded | Invoke the matching skill (`backend-create-rest-api`, `frontend-implement-ui-component`) |
| You want to delegate a whole vertical to a specialist | Spawn the `backend-developer` or `frontend-developer` agent |
| You want to verify the branch is green | Skills `backend-verify` + `frontend-verify` |
| You want to self-review before PR | Skill `code-review` |
| You want to open the PR | Skill `create-merge-request` |
| The product spec changed | Follow the **Update flow** in §3 below |
| You learned a coding rule the hard way | Tighten [.claude/rules/](../.claude/rules/) — codify the lesson |
| You made an architectural decision | Write an ADR under [docs/decisions/](../docs/decisions/) |

> Each of the procedures below has a matching copy-paste prompt under [prompts/](prompts/) — so you can run them in a fresh Claude session the same way the bootstrap steps work. See [§7 Prompts index](#7-prompts-index) at the bottom of this file for the full map.

---

## 1. The daily development loop

Use one mental model: **Plan → Implement → Verify → Review → Ship**. Each phase has a dedicated entry point.

```
┌─────────────────────────────────────────────────────────────────┐
│  1. PLAN          /make-plan "<feature>"                        │
│                   → writes docs/plans/implementation-plan-X.md   │
│                   → you review, edit, accept                     │
└────────────────────────────────────┬────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. IMPLEMENT     /implement-plan docs/plans/…X.md               │
│                   → main session delegates to specialist agents  │
│                   → agents invoke skills (scaffold, test, etc.)  │
└────────────────────────────────────┬────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. VERIFY        Skill backend-verify  /  Skill frontend-verify │
│                   → fail loud, fix root cause                    │
└────────────────────────────────────┬────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. REVIEW        Skill code-review                              │
│                   → MUST/MUST NOT findings vs .claude/rules/      │
│                   → security.md §12 checklist line-by-line       │
└────────────────────────────────────┬────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. SHIP          Skill create-merge-request                     │
│                   → drafts Conventional-Commits PR title + body  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.1 Plan — `/make-plan`

For anything larger than a one-file change, start with a plan.

- **When:** new endpoint, new screen, new consumer, refactor crossing modules, anything you'd want a teammate to review before you start.
- **What you get:** a markdown plan under [docs/plans/](../docs/plans/) listing files to touch, the order to touch them in, the test strategy, and the rule citations that constrain the work.
- **Your job:** *read the plan*. The point of the plan is to surface assumptions cheaply. Wrong assumption fixed in the plan is one edit; wrong assumption fixed after the implementation is a rewrite.
- **Skip the plan when:** the work is one file, one method, one bug, one rename. Planning overhead exceeds the work.

### 1.2 Implement — `/implement-plan`

Hand the accepted plan to `/implement-plan`. The command reads the plan, delegates each chunk to the right specialist agent, and reports back.

- Each agent (`backend-developer`, `frontend-developer`) is sandboxed to its own stack — backend never edits TS, frontend never edits Java — and is briefed with the NFR contract.
- The agents call skills (`backend-create-rest-api`, `backend-create-unit-test`, …) so that the scaffolding shape stays consistent with the rules.
- **Your job:** trust but verify. After each agent reports, glance at the diff. If something contradicts a rule, fix the rule or fix the diff — never both.

### 1.3 Verify — `backend-verify` / `frontend-verify`

Run the verify skills before review, not after.

- `backend-verify` runs `./mvnw verify`, surfaces unit/integration/JaCoCo gate failures with a PASS/FAIL verdict.
- `frontend-verify` runs `pnpm lint && pnpm build && pnpm test` (CI-flagged, non-interactive).
- A flake is a defect. Quarantine under a ticket, do not re-run until green.

### 1.4 Review — `code-review`

Run `code-review` on the diff before opening the PR. It walks every rule file (`backend_coding.md`, `frontend_coding.md`, `security.md`, `testing.md`, `upgrade-policy.md`) against the changed lines and emits findings with severity:

| Severity | Source |
|---|---|
| **block** | `MUST` / `MUST NOT` / `Never` |
| **warn** | `Prefer` / `Avoid` |
| **info** | `<!-- not-yet-adopted -->` rules |

Fix every **block** before opening the PR. The PR reviewer (human) is your second line, not your first.

### 1.5 Ship — `create-merge-request`

`create-merge-request` does the housekeeping: pre-flight checks, push with upstream, draft a Conventional-Commits title and body (Summary / Changes / Test Plan / Risk).

You still hit "open" yourself — the skill does not auto-publish.

### 1.6 When to bypass the loop

Not every change needs the full ceremony.

| Scope | Loop |
|---|---|
| New vertical (endpoint + UI + tests) | Plan → Implement → Verify → Review → Ship |
| New endpoint only | Skill `backend-create-rest-api` → Verify → Review → Ship |
| New UI component only | Skill `frontend-implement-ui-component` → Verify → Review → Ship |
| Bug fix, one file | Edit → Verify → Review → Ship |
| Rename / formatting | Edit → Verify → Ship |
| Docs-only edit | Edit → Ship |

The rule is: **the loop scales to the work**. Don't drag a one-line fix through `/make-plan`; don't ship a feature through a bare edit.

---

## 2. Keeping artefacts in sync as code lands

The bootstrap defaults to **greenfield**. Every `(verify)` and `(spec — not yet implemented)` marker is a promise to a future contributor. As real code lands, those promises need to be closed.

### 2.1 The `(verify)` sweep

After any PR that adds or moves real code, do a quick sweep. The automated version is [prompts/ongoing-verify-marker-sweep.md](prompts/ongoing-verify-marker-sweep.md) — paste it into a fresh session and it produces a triage report at `docs/plans/marker-sweep-<date>.md`. Manual fast-path:

```
grep -rn "(verify)\|spec — not yet implemented" docs/ .claude/rules/
```

For each hit, do one of:

1. **The fact is now true** — delete the marker, replace any placeholder path with the real one.
2. **The fact is wrong** — fix it. The PR that landed the code is the right place to fix the doc.
3. **The fact is still aspirational** — leave the marker. Greenfield projects carry them for months.

### 2.2 When a rule was wrong

A `code-review` finding that the team rejects ("this isn't actually our convention") is a signal that the **rule** is wrong, not the diff. Tighten — or relax — the rule in the same PR. Rules drift the moment they outvote reality.

Procedure:

1. Edit the rule under [.claude/rules/](../.claude/rules/).
2. If the rule cites a `docs/` section, check the citation still holds.
3. If the change is large enough to need rationale, write an ADR under [docs/decisions/](../docs/decisions/).
4. Note the change in the PR description so reviewers see both the rule edit and the code that proved it wrong.

### 2.3 When you learn a new pattern

A pattern you used three times this sprint is a pattern. Codify it once instead of re-discovering it next sprint.

| Pattern type | Where it lands |
|---|---|
| One-off procedure ("scaffold a Kafka consumer") | New skill: [.claude/skills/`<slug>`/SKILL.md](../.claude/skills/) |
| User-invoked workflow ("`/check-coverage`") | New command: [.claude/commands/`<slug>`.md](../.claude/commands/) |
| Specialist domain ("infra-developer") | New agent: [.claude/agents/`<slug>`.md](../.claude/agents/) |
| Cross-cutting convention | Add a section to the matching rule file |
| Architectural decision | New ADR under [docs/decisions/](../docs/decisions/) |

Use [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md) as the decision tree when in doubt.

---

## 3. Update flow when `project-info.md` changes

[project-info.md](../project-info.md) is **scaffolding**, not the source of truth — `CLAUDE.md` + [docs/](../docs/) are. But the team will keep editing it as the product evolves (new FR, new NFR, new stack component, closed ADR). When it changes, the change must propagate **downstream**, in order, or the artefacts will drift apart.

### 3.1 Classify the change first

Not every edit triggers a full propagation. Classify before you act:

| Class | Examples | Propagation depth |
|---|---|---|
| **Cosmetic** | Typo, rephrased sentence, added a "(see X)" cross-link | None. Commit the edit alone. |
| **Spec-level** | New FR, new NFR, new endpoint, new domain term, role added, ADR closed | Down to `docs/` and `.claude/rules/` if a rule cites the changed section. |
| **Stack-level** | New tech (e.g. add ClickHouse), version bump (Java 21 → 25), framework change | Down to `CLAUDE.md` + `docs/architecture/` + `.claude/rules/upgrade-policy.md §1` + any rule citing the stack. |
| **Architectural** | Replaced one stream with another, changed an NFR contract, swapped concurrency strategy | Full re-run of the affected step's bootstrap prompt **plus** an ADR. |
| **Goal pivot** | Different product, different users, different mandate | Treat as a new project — re-run the bootstrap from step 1. Rare. |

If you cannot tell which class a change belongs to, treat it one level up. Over-propagating is cheaper than under-propagating.

### 3.2 The propagation order — top-down

Run propagation in the **same order** the bootstrap ran, from cheapest to deepest. Stop at the first layer that doesn't cite the changed section.

```
project-info.md  ──►  CLAUDE.md  ──►  docs/  ──►  .claude/rules/  ──►  skills / commands / agents
                                       │              │
                                       │              └─ check every rule that cites the
                                       │                 changed docs/ section
                                       │
                                       └─ identify the docs/ section that "owns" the change
                                          (business-rules / architecture / decisions / api /
                                           database / domain-knowledge)
```

The propagation contract:

| If you edit `project-info.md §<X>` … | … you MUST also touch … |
|---|---|
| §1 Identity, §2 Stakeholders | `CLAUDE.md` (project status) |
| §3 Architecture | `CLAUDE.md` (Architecture block) + `docs/architecture/README.md` |
| §3.1 Module layout | `CLAUDE.md` (Module layout) + `docs/architecture/README.md §3` |
| §4 Tech stack | `CLAUDE.md` (Tech Stack block) + `docs/architecture/README.md §2` + `.claude/rules/upgrade-policy.md §1` |
| §5 FRs (Epic / FR added / changed) | `docs/business-rules/<epic>-rules.md` + `docs/api/README.md` if the FR has an endpoint |
| §6 NFR added / weakened / strengthened | `CLAUDE.md` (Invariants block) + `docs/business-rules/README.md` NFR matrix + every rule that cites the NFR |
| §7 External integrations | `docs/architecture/README.md` + `docs/decisions/` (ADR for the integration) + `.claude/rules/security.md` if auth/secret-related |
| §8 Security baseline | `.claude/rules/security.md` (cite the new section) + `docs/decisions/` if a new mechanism |
| §9 Domain glossary | `docs/domain-knowledge/glossary.md` |
| §10 ADR closed | `docs/decisions/<NNNN>-<slug>.md` flips to Accepted; mark `Supersedes:` if applicable |
| §13 Conventions | `.claude/rules/backend_coding.md` and/or `.claude/rules/frontend_coding.md` |
| §14 Env / config | `docs/architecture/README.md §7` + `.claude/rules/security.md §1` if a secret |
| §16 Open question closed | The corresponding ADR file under `docs/decisions/` |

Cross-references are the glue. **Cite, don't duplicate** — if `.claude/rules/security.md` says "see project-info §8", a §8 edit just needs the cite to keep resolving. If the rule restates the rule inline, the rule has drifted and must be re-aligned.

### 3.3 The two propagation modes — targeted edit vs prompt rerun

You have two tools, and they have very different costs.

| Mode | Cost | When |
|---|---|---|
| **Targeted edit** (you / Claude in main session) | Low — minutes | Spec-level edits, single-section changes, marker cleanup |
| **Bootstrap prompt rerun** (open fresh Claude session, paste the matching `prompts/step-N-*.md`) | High — full step | Stack-level, architectural, or when targeted edits would touch ≥5 sections |

The rule of thumb:

- If the change touches **≤2 files** downstream, edit them directly.
- If it touches **3–4 files**, edit directly but ask Claude to do the propagation in one prompt (cite the `project-info.md` diff).
- If it touches **≥5 files** or rewrites a whole section, **rerun the bootstrap prompt** for the affected layer in a fresh session. This is what the prompts were designed for.

### 3.4 The end-to-end update procedure

A clean, repeatable procedure for any spec change. The automated version is [prompts/ongoing-propagate-project-info-change.md](prompts/ongoing-propagate-project-info-change.md) — edit `project-info.md` and leave it **uncommitted** (staged or unstaged is fine), then paste the prompt into a fresh session. It detects the change by running `git diff HEAD -- project-info.md` against the last commit, classifies the change, locates downstream citations (including the OLD wording that may still linger in rule files), produces surgical edits, and prints a PR-body fragment.

```
0. Open a feature branch.

1. EDIT  project-info.md  — LEAVE IT UNCOMMITTED.
   - Bump §16 if an open question closed.
   - Mark old rows as ❌ Superseded by §X if you replaced one.
   - Do NOT `git commit` yet. The propagation prompt detects the change via
     `git diff HEAD -- project-info.md`, which only sees the diff if the
     edit is still in the working tree (staged or unstaged).

2. RUN  prompts/ongoing-propagate-project-info-change.md  in a fresh Claude session.
   - The prompt classifies the change (cosmetic / spec / stack / architectural / pivot).
   - For cosmetic / pivot it exits with guidance; for the other three it continues.
   - It locates downstream citations (propagation table + grep for old wording).
   - It produces SURGICAL edits to each downstream file — also uncommitted.
   - It prints a propagation report + PR-body fragment.

3. REVIEW  the staged edits in your editor or with `git diff`.
   - Per-file diff should be small (one citing sentence / row / section).
   - If any diff is > ~20 lines, suspect a "while you're there" rewrite — push back.
   - If the prompt recommended a bootstrap prompt rerun (architectural change,
     ≥5 downstream files), run that rerun NOW in another fresh session before
     committing — the rerun is what edits the affected layer in bulk.

4. ADR  if the change reflects a decision with trade-offs.
   - The prompt drafts/flips it for you; you decide Proposed vs Accepted.
   - If it supersedes a previous ADR, add Supersedes: / Superseded by: markers.

5. VERIFY  the artefacts still cross-link.
   - Re-run `grep -rn "<old wording>" docs/ .claude/rules/ CLAUDE.md` to confirm
     the old wording is gone everywhere.
   - Run Skill code-review on the docs diff — it catches stale rule citations.
   - Run prompts/step-7-validate-and-iterate.md if architectural change.

6. COMMIT  in this order — source first, then each downstream layer separately:
   a. git add project-info.md            && git commit -m "docs: update project-info.md §<X> — <reason>"
   b. git add CLAUDE.md                  && git commit -m "docs: propagate §<X> into CLAUDE.md"
   c. git add docs/                      && git commit -m "docs: propagate §<X> into docs/"
   d. git add .claude/rules/             && git commit -m "claude: propagate §<X> into rules"
   e. (optional) git add docs/decisions/ && git commit -m "docs: <draft|accept> ADR NNNN <slug>"

   Reviewers walk the chain top-to-bottom: source change in (a), propagation
   evidence in (b)–(d), ADR decision in (e).

7. PR  via Skill create-merge-request.
   - Paste the prompt's PR-body fragment into the body.
   - The PR description MUST list the propagation chain explicitly.
```

### 3.5 What to do when you find drift after the fact

If you discover `CLAUDE.md` says X but `project-info.md §<Y>` says X', you have two routes:

1. **The spec is right, the briefing is stale.** Fix `CLAUDE.md` (and any downstream artefact carrying the same staleness) in a `docs:` commit. Note the drift in the PR description so the team learns the lesson.
2. **The briefing is right, the spec is stale.** This is the more common case after a few sprints — `project-info.md` is "scaffolding, not source of truth" by design. Either re-sync the spec from `CLAUDE.md` + `docs/`, or **archive** `project-info.md` and stop maintaining it (see [03-initialization-workflow.md §After step 7](03-initialization-workflow.md#after-step-7) — "Archive or delete `project-info.md`").

The wrong fix is to silently align one to the other and walk away. Drift surfaces because two readers diverge; the fix is to record what changed and why.

---

## 4. Adding artefacts after bootstrap

The bootstrap creates a baseline. The project will grow new skills, new commands, new agents. The mental model is the same as during bootstrap, just one artefact at a time. The automated version is [prompts/ongoing-add-artefact.md](prompts/ongoing-add-artefact.md) — it expects five inputs (type, slug, purpose, triggers, exclusions), validates against the decision tree in [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md), checks for overlap, and copies from a sibling template.

### 4.1 Adding a new skill

When: you've done the same procedure ≥3 times manually.

1. Copy an existing skill under [.claude/skills/](../.claude/skills/) as a template.
2. Edit `SKILL.md` — frontmatter (`name`, `description`, `triggers`), then the procedure body.
3. Cite the rules the procedure must respect (don't restate them).
4. Test it by invoking it in a fresh session with a realistic input.

### 4.2 Adding a new command

When: you have a user-facing workflow that wraps multiple skills.

1. Copy [.claude/commands/make-plan.md](../.claude/commands/make-plan.md) as a template.
2. The command body composes skills; keep coding rules out of it.
3. Test by typing `/<command>` in a fresh session.

### 4.3 Adding a new agent

When: you've added a new specialist domain (e.g. an infra layer, a data-pipeline layer) with its own stack and rules.

1. Copy [.claude/agents/backend-developer.md](../.claude/agents/backend-developer.md) as a template.
2. Frontmatter: `name`, `description` (with exclusion rules — what NOT to route to it), `tools`, `model`.
3. Body: cite the rule files, the skills the agent owns, and the NFR contract.
4. Test by delegating a realistic task with the `Agent` tool.

### 4.4 Adding a new rule

Two questions decide where it goes:

1. Is this a **runtime business rule**? → [docs/business-rules/](../docs/business-rules/).
2. Is this a **coding contract** (how to write the code)? → [.claude/rules/](../.claude/rules/).

If it's neither — a description of *what* the system does — it's [docs/architecture/](../docs/architecture/) or [docs/api/](../docs/api/) or [docs/database/](../docs/database/).

Never put the same rule in two places. Cite from one to the other.

---

## 5. Health checks (do these monthly)

Once you're three months in, the playbook will reward a recurring sanity pass. The automated version is [prompts/ongoing-baseline-health-check.md](prompts/ongoing-baseline-health-check.md) — it walks the six checks below, captures measurements, assigns PASS / WARN / FAIL verdicts, and writes a dated report under `docs/plans/`.

| Check | How | Frequency |
|---|---|---|
| `(verify)` markers count is trending down, not up | `grep -rc "(verify)" docs/ .claude/rules/ \| wc -l` | Monthly |
| Every ADR has a status (Proposed / Accepted / Superseded) | `grep -L "^Status:" docs/decisions/*.md` | Monthly |
| Skills still invoke correctly | Run each Skill in a smoke test session | Quarterly |
| `CLAUDE.md` is still < ~300 lines | `wc -l CLAUDE.md` | Monthly |
| `project-info.md` and `CLAUDE.md` don't contradict | Eyeball the §6 NFRs vs the Invariants block | Quarterly |
| No new dependency without an upgrade-policy entry | Diff `pom.xml` / `package.json` vs `.claude/rules/upgrade-policy.md §1` | Per PR (code-review skill catches it) |

If the project is healthy, every check passes in minutes. If a check takes more than 15 minutes, the playbook has drifted — schedule a "claude-baseline-refresh" sprint and walk back through step 7 of [03-initialization-workflow.md](03-initialization-workflow.md).

---

## 6. Frequently asked questions

**Do I have to write a plan for every change?**
No. See §1.6. Plan when the work is multi-file; skip when it's not.

**Can the specialist agents edit `.claude/rules/`?**
No. Agents are scoped to code under their stack folder. Rule edits are a main-session concern — they affect every agent, so the main session owns them.

**A teammate edited `project-info.md` but didn't propagate. What's the cleanup?**
Run the §3.4 procedure on their branch (or in a follow-up PR). Don't accept the spec edit alone — drift is the cost.

**`/make-plan` produced a plan I disagree with. Do I rerun?**
Yes. Edit `project-info.md` or `docs/` to reflect the constraint you missed (if it was a missing constraint), then rerun. If the plan was just a bad guess, write your own and feed it to `/implement-plan` directly.

**An ADR was wrong six months in. How do I retract it?**
Don't delete it. Write a new ADR that supersedes it, set the new ADR's `Status: Accepted, Supersedes: NNNN`, and the old ADR's `Status: Superseded by NNNN`. ADRs are an audit trail, not a wiki.

**Can I run the bootstrap prompts in parallel after the initial run?**
Same answer as during bootstrap — no. Each prompt is an input to the next. The only safe parallelism is across independent feature branches, not across artefact layers within one branch.

**My team isn't using slash commands. Are the commands wasted?**
The commands are entry points; the actual work lives in skills and agents. If a teammate prefers to invoke the skill directly ("run code-review"), that's identical behaviour. The commands exist so the workflow has *one* canonical surface, not because the surface is mandatory.

---

## 7. Prompts index

Each procedure in this file has a copy-paste prompt under [prompts/](prompts/). Pattern: open a fresh Claude Code session at the repo root, paste the prompt body verbatim, review the output, commit per the prompt's "Commit" section.

| Procedure | Section in this file | Prompt | When |
|---|---|---|---|
| Propagate a `project-info.md` change | §3 | [prompts/ongoing-propagate-project-info-change.md](prompts/ongoing-propagate-project-info-change.md) | Any time `project-info.md` changes beyond cosmetic |
| `(verify)` marker sweep | §2.1 | [prompts/ongoing-verify-marker-sweep.md](prompts/ongoing-verify-marker-sweep.md) | After a PR that lands real code under `backend/` or `frontend/` |
| Baseline health check | §5 | [prompts/ongoing-baseline-health-check.md](prompts/ongoing-baseline-health-check.md) | Monthly (or immediately on suspected drift) |
| Add a new artefact (skill / command / agent / rule) | §4 | [prompts/ongoing-add-artefact.md](prompts/ongoing-add-artefact.md) | One artefact at a time |

Distinguishing rule:

- **`prompts/step-N-*.md`** — sequential, run once at bootstrap, in order.
- **`prompts/ongoing-*.md`** — event-driven, run as the trigger fires, in any order.

Both share the same shape (When / Output / Prereqs → What this produces → Copy-paste prompt → How to review → Commit) so the muscle memory carries across.

---

## Further reading

- [README.md](README.md) — the playbook overview
- [03-initialization-workflow.md](03-initialization-workflow.md) — the bootstrap that comes before this file
- [reference/what-each-artifact-is-for.md](reference/what-each-artifact-is-for.md) — decision tree for "where does this go"
- [reference/file-naming-conventions.md](reference/file-naming-conventions.md) — slugs, kebab-case, anchors

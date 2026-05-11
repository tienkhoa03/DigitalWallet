# Step 1 — Bootstrap `CLAUDE.md`

**When to run:** after `project-info.md` is filled and committed.
**Output:** `/CLAUDE.md` at the repo root.
**Estimated time:** 5–10 minutes.
**Prerequisites:** §0 of [03-initialization-workflow.md](../03-initialization-workflow.md) complete.

---

## What this step produces

`CLAUDE.md` is the top-of-context briefing Claude Code loads on every session. It is **not** documentation — it's a tight summary of:

- Project status (greenfield vs. existing code).
- Mandated tech stack (with non-negotiables).
- Architecture in 1–2 paragraphs (the streams or major paths).
- Non-negotiable invariants (the NFRs in one block).
- Commands (build, test, run) — placeholder if no build tooling yet.

It is **read on every interaction**, so it must be terse and accurate. ≤300 lines is a strong target.

---

## Copy-paste prompt

> Paste the following block, in its entirety, into a fresh Claude Code session opened at the project root.

```
You are bootstrapping `CLAUDE.md` for this repository.

INPUT:
- Read `project-info.md` at the repo root. It is the single source of truth for this step.
- Read `claude-guidelines/01-project-info-template.md` ONLY if `project-info.md` references "see template §X" — otherwise ignore.

GOAL:
Produce `/CLAUDE.md` at the repo root. Optimize for:
- terseness — every line earns its place
- accuracy — only state facts that `project-info.md` supports
- enforceability — invariants must be specific enough to block a PR

STRUCTURE (mandatory sections, in this order):

# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status
- One paragraph derived from `project-info.md §1`. State whether code exists yet, what the design contract is (link to the spec from `§15`), and what to treat as authoritative when there is a conflict.

## Tech Stack (Mandated by Spec)
- One bullet per stack component from `§4`. Format: **<Concern>:** <Tech name + version + key extensions/libraries>. <Constraint or "do not substitute X">.
- Cover backend, persistence, messaging, cache, frontend (if present), testing, deployment.

## Architecture
- One paragraph synthesised from `§3` describing the **shape** of the system — module organisation, major paths, what decouples them.
- If `§3` declares multiple parallel streams or paths, give each a sub-section (e.g. "### Synchronous stream", "### Asynchronous stream").
- Sub-sections describe each stream in ≤8 bullet points.

## Non-Negotiable Invariants
- One bullet per NFR row in `§6`. Format: **<Concept>:** <one-line rule>. Treat any change that weakens them as a regression.
- Aim for 3–7 bullets.

## Commands
- If `§4.6` lists build tools, populate with `<build-tool> <lifecycle>` commands (e.g. `./mvnw test`, `npm run lint`).
- If no build tooling is committed, write: "No build tooling is committed yet. When introducing it, use <choice from §4.1 / §4.2>. Add the standard lifecycle commands here once they exist:" followed by the planned commands as placeholders.

## (Optional sections — include only if `project-info.md` has corresponding content)
- "Domain glossary at a glance" — top 5–7 terms from `§9`, one-line each.
- "Module layout (planned)" — ASCII tree from `§3.1` if a layout was declared.

CONSTRAINTS:
- Do NOT exceed ~300 lines.
- Do NOT include the entire FR list — those go in `docs/business-rules/` in step 2.
- Do NOT include the full glossary — those go in `docs/domain-knowledge/` in step 2.
- Do NOT invent technology choices. If `§4` says "unspecified", state "unspecified by spec — see ADRs once chosen".
- Do NOT use emojis.
- Cross-reference future docs/rules with placeholder paths like `[.claude/rules/backend_coding.md](.claude/rules/backend_coding.md)` — they will land in later steps.
- Mark anything not yet implemented with `(spec — not yet implemented)` so contributors don't grep for files that don't exist.

OUTPUT:
1. Write `/CLAUDE.md`.
2. Print a 5-bullet summary of what's inside.
3. Flag any section of `project-info.md` that was too vague to translate cleanly — under the heading "## Gaps in project-info.md to address before step 2".

DO NOT:
- Read or write any other files in this session.
- Create a git commit (the user will commit manually).
- Start on step 2 — this session ends after writing CLAUDE.md.
```

---

## How to review the output

Spend ~5 minutes on the diff before committing:

| Check | Pass criterion |
|---|---|
| `CLAUDE.md` is < 300 lines | `wc -l CLAUDE.md` |
| Every NFR from `§6` of `project-info.md` appears in the Invariants block | Skim both side-by-side. |
| No invented version numbers | Spot-check 3 versions — e.g. "Java 21" matches `§4.1`. |
| No invented file paths without `(spec — not yet implemented)` | grep for any path that looks like a Java/TS file. |
| The "Gaps" section is empty or short (<5 entries) | If long, fix `project-info.md` and rerun. |

If any check fails, **rerun the prompt in a fresh session** after fixing `project-info.md`. Do not patch the output by hand.

---

## Commit

```bash
git add CLAUDE.md
git commit -m "docs: add CLAUDE.md briefing"
```

Then move to [step-2-bootstrap-docs.md](step-2-bootstrap-docs.md).

# Ongoing — Propagate a `project-info.md` Change

**When to run:** any time [project-info.md](../../project-info.md) changes beyond a cosmetic edit (new FR, new NFR, new tech in §4, ADR closed in §10, glossary term added in §9, etc.).
**Output:** edits to `CLAUDE.md`, [docs/](../../docs/), [.claude/rules/](../../.claude/rules/), and possibly new [docs/decisions/](../../docs/decisions/) entries, separated into per-layer commits.
**Estimated time:** 15–45 minutes for a spec-level change; up to ~1 hour for a stack-level change.
**Prerequisites:**
- Bootstrap completed (see [03-initialization-workflow.md](../03-initialization-workflow.md)).
- [04-ongoing-development-workflow.md §3](../04-ongoing-development-workflow.md#3-update-flow-when-project-infomd-changes) read once.
- The edit to `project-info.md` is **already committed** on a feature branch (so Claude can diff it against `main`).

---

## What this step produces

Three things, in this order:

1. A **propagation report** — a short markdown summary of the classified change (cosmetic / spec / stack / architectural / pivot), the affected downstream files, and the chosen mode (targeted edit vs. bootstrap prompt rerun).
2. **Targeted edits** in the downstream artefacts that cite the changed section. Each edit is surgical — touching only the citing sentence or table row.
3. A **PR-body fragment** listing the propagation chain (`edited §<X> → updated <files>`) so reviewers can audit completeness.

This prompt does **not** create commits — it stages edits the user reviews and commits per layer.

---

## Copy-paste prompt

> Paste the following block, in its entirety, into a fresh Claude Code session opened at the project root.

```
You are propagating a `project-info.md` change through the AI artefacts.

INPUT:
- The current branch HEAD against `main` — diff `project-info.md` to see what changed.
  Run: git diff main -- project-info.md
- Read `claude-guidelines/04-ongoing-development-workflow.md` §3 (classification table + propagation contract + procedure). It is the authoritative procedure for this step.
- Read `CLAUDE.md` and `docs/` only as needed to locate citing sections.

GOAL:
Propagate the `project-info.md` diff downstream — surgically, layer by layer — and produce a propagation report the user can paste into the PR description.

PROCEDURE:

1. CLASSIFY  the change using the table in §3.1 of `04-ongoing-development-workflow.md`:
   - cosmetic   → stop. Print "Classification: cosmetic — no propagation needed." and exit.
   - spec       → continue.
   - stack      → continue.
   - architect. → continue, but recommend a bootstrap prompt rerun at the end (see §3.3).
   - pivot      → stop. Print "Classification: pivot — recommend re-running the full bootstrap from step 1." and exit.

2. LOCATE downstream citations.
   - For each `project-info.md §<X>` row in the diff, use the propagation table in §3.2 to list the artefacts that MUST be touched.
   - For each listed artefact, grep for citations of the changed section (e.g. `grep -rn "project-info.md §6"` for an NFR change) and add any hits the propagation table missed.
   - Report the union as a "downstream files" list.

3. CHOOSE the mode using §3.3:
   - ≤2 downstream files → targeted edit.
   - 3–4 files          → targeted edit, but flag for review.
   - ≥5 files OR a whole section rewritten → recommend a bootstrap prompt rerun in a fresh session
     (point at the matching `prompts/step-N-*.md`) and stop the targeted-edit pass for that layer.

4. EDIT each downstream file. Rules:
   - Edits MUST be surgical — change only the citing sentence, table row, bullet, or section.
   - Do NOT rewrite a section "while you're there".
   - If a rule's wording becomes inconsistent with the new spec, fix the wording — never silently swap to the new value mid-rule.
   - If a rule restates a fact (instead of citing it), replace the restatement with a citation back to `project-info.md §<X>` or to the owning `docs/` section.
   - If an NFR was renumbered, update every "NFR<old>" reference, not just the one in the citing table.

5. ADR  — if the change reflects a decision with trade-offs (new tech in §4, ADR closed in §10, security mechanism in §8):
   - If `docs/decisions/<NNNN>-<slug>.md` already exists for the ADR, flip its `Status:` to `Accepted` and date it.
   - If it doesn't, draft a new ADR using `docs/decisions/template.md`. Status: `Proposed`. Note that the user will flip it to `Accepted` on merge.
   - If it supersedes a previous ADR, add `Supersedes: <NNNN>` / `Superseded by: <NNNN>` markers in BOTH files.

6. VERIFY  cross-links resolve:
   - For every edited file, scan its updated section for `[..](path)` links and confirm the targets exist.
   - Flag any link that points to a file or anchor that doesn't resolve.

OUTPUT (print to chat, in this order):

A. PROPAGATION REPORT
   ```
   Classification: <cosmetic|spec|stack|architectural|pivot>
   project-info.md changes:
     - §<X> <one-line summary>
     - §<Y> <one-line summary>
   Downstream files touched (targeted edit):
     - CLAUDE.md (<which section>)
     - docs/<file>.md (<which section>)
     - .claude/rules/<file>.md (<which section>)
   Downstream layers recommended for bootstrap-prompt rerun:
     - <none | prompts/step-N-*.md because <reason>>
   ADR action:
     - <none | flipped docs/decisions/NNNN to Accepted | drafted docs/decisions/NNNN as Proposed>
   Unresolved cross-links:
     - <none | <path>>
   ```

B. PER-FILE DIFF SUMMARY
   For each file edited, print 1–3 bullets describing the surgical edit. Do NOT print the full diff.

C. PR-BODY FRAGMENT (in a fenced markdown block)
   ```
   ## Propagation
   Edited `project-info.md §<X>` → propagated to:
   - `CLAUDE.md` (<section>)
   - `docs/<file>.md` (<section>)
   - `.claude/rules/<file>.md` (<section>)
   Bootstrap prompt reruns recommended: <none | …>
   ADRs: <none | NNNN flipped to Accepted | NNNN drafted as Proposed>
   ```

D. NEXT STEPS
   1. Review the edits — they were staged, not committed.
   2. Commit per layer per §3.4 step 6 of `04-ongoing-development-workflow.md`:
      - one commit for `project-info.md` (already committed if the user followed the procedure)
      - one commit per downstream layer (`CLAUDE.md`, `docs/`, `.claude/rules/`)
   3. If a bootstrap prompt rerun was recommended, open a fresh Claude session and run it now.
   4. Open the PR via Skill `create-merge-request`, pasting the PR-body fragment into the body.

DO NOT:
- Create any git commits.
- Touch files outside the downstream list you produced in step 2.
- Edit `project-info.md` itself — it is the source of the diff, not a propagation target.
- Run `prompts/step-N-*.md` automatically. Recommend, do not execute.
- Suppress edits because "it's only a docs change" — the propagation contract is non-negotiable.
```

---

## How to review the output

Spend ~5 minutes before committing the per-layer edits:

| Check | Pass criterion |
|---|---|
| Classification is correct | Eyeball: a new NFR is spec-level (not cosmetic); a Java→Kotlin swap is architectural (not stack). |
| Every artefact in the propagation table for the edited `§<X>` appears in the report | Side-by-side with §3.2 of `04-ongoing-development-workflow.md`. |
| Per-file edits are surgical (one citing sentence / row / section) | If the diff for any file is > ~20 lines, suspect a "while you're there" rewrite — push back. |
| No restated facts left over after the propagation | Grep the edited rule files for the OLD value (old NFR number, old version, old role name) — should be zero hits. |
| Unresolved cross-links is empty | If non-empty, fix or escalate before commit. |
| If the change was architectural, a bootstrap prompt rerun is recommended | The report names a specific `prompts/step-N-*.md`. |

If any check fails, **rerun this prompt in a fresh session** after fixing the underlying issue. Do not patch the output by hand and commit silently — the propagation chain becomes opaque to reviewers.

---

## Commit

The prompt does not commit. The user commits per layer per [04-ongoing-development-workflow.md §3.4 step 6](../04-ongoing-development-workflow.md#34-the-end-to-end-update-procedure):

```bash
# project-info.md (already committed before running this prompt)
git commit -m "docs: update project-info.md §<X> — <short reason>"

# per downstream layer
git add CLAUDE.md
git commit -m "docs: propagate §<X> change into CLAUDE.md"

git add docs/
git commit -m "docs: propagate §<X> change into docs/"

git add .claude/rules/
git commit -m "claude: propagate §<X> change into rules"
```

Then open the PR with Skill `create-merge-request`, pasting the PR-body fragment from the prompt output.

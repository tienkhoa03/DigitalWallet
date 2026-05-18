# Ongoing — `(verify)` Marker Sweep

**When to run:** after any PR that adds or moves real code under `backend/` or `frontend/`. Most useful at the end of a sprint or when the project leaves "early greenfield" and starts having real file paths.
**Output:** a sweep report listing every `(verify)` and `(spec — not yet implemented)` marker in [docs/](../../docs/) and [.claude/rules/](../../.claude/rules/), classified as "now true → delete marker", "wrong → fix", or "still aspirational → leave".
**Estimated time:** 10–30 minutes depending on how many markers exist.
**Prerequisites:**
- Bootstrap completed (see [03-initialization-workflow.md](../03-initialization-workflow.md)).
- The PR that landed the code is already merged into `main` (or you're running this on the same branch immediately before merging).

---

## What this step produces

A markdown report at `docs/plans/marker-sweep-<yyyy-mm-dd>.md` containing three sections:

1. **Now true → delete the marker.** Each item: file path, line number, current line, suggested rewrite.
2. **Wrong → fix the fact.** Each item: file path, line number, what the doc claims, what the code actually shows, suggested edit.
3. **Still aspirational → leave the marker.** Each item: file path, line number, one-line justification.

The prompt **does not edit files** — it produces a triage report the user reviews before applying.

---

## Copy-paste prompt

> Paste the following block, in its entirety, into a fresh Claude Code session opened at the project root.

```
You are sweeping the project for stale `(verify)` and `(spec — not yet implemented)` markers.

INPUT:
- Read `claude-guidelines/04-ongoing-development-workflow.md` §2.1 for the procedure.
- Run: grep -rn -E "\(verify\)|spec — not yet implemented" docs/ .claude/rules/ CLAUDE.md
  This is the canonical list of markers to triage.
- Read each hit's surrounding ~10 lines to understand the claim being marked.
- For each hit, look at the actual codebase (under `backend/` or `frontend/`) to determine whether the claim is now true, wrong, or still aspirational.

GOAL:
Produce a triage report classifying every marker. The user will apply the edits in a follow-up step.

PROCEDURE:

1. ENUMERATE markers via the grep above. Note: a marker on a line that has been deleted in a recent PR is also stale — flag it.

2. CLASSIFY each marker into exactly one bucket:

   - NOW TRUE — the claim is now reflected in the code (file exists, behaviour is implemented, dependency is on the pom/package.json).
     Action: delete the marker, replace any placeholder path with the real one.

   - WRONG — the code shows the claim is incorrect (file path differs, behaviour differs, dependency was substituted).
     Action: fix the fact AND remove or move the marker.

   - STILL ASPIRATIONAL — no code yet for this claim; the marker is doing its job.
     Action: leave it. Note ONE-LINE justification ("FR4.2 PFM consumer not scaffolded yet").

3. VERIFY each classification by spot-checking:
   - NOW TRUE: confirm by reading the cited file in the codebase. Cite the file path + line number that proves it.
   - WRONG: confirm by reading the cited file. Cite the divergence.
   - STILL ASPIRATIONAL: confirm that no file matches the claim. Grep for the file name; expect zero hits.

4. WRITE the report to `docs/plans/marker-sweep-<yyyy-mm-dd>.md` with these sections:

   # Marker Sweep — <yyyy-mm-dd>

   ## Summary
   - Total markers: <N>
   - Now true: <A>
   - Wrong: <B>
   - Still aspirational: <C>
   - Trend vs last sweep (if a prior `marker-sweep-*.md` exists): ↓ <delta> or ↑ <delta>

   ## 1. Now true → delete the marker
   For each item:
   ```
   - File: docs/architecture/README.md
     Line: 142
     Current: "The outbox poller drains every 5s `(verify)`."
     Evidence: backend/shared/outbox/OutboxPoller.java:34 — `@Scheduled(every = "5s")`
     Suggested edit: remove `(verify)`.
   ```

   ## 2. Wrong → fix the fact
   For each item:
   ```
   - File: .claude/rules/backend_coding.md
     Line: 87
     Current: "Wallet entities live in `backend/wallet/persistence/Wallet.java` (spec — not yet implemented)."
     Code reality: the entity lives at `backend/wallet/persistence/WalletEntity.java`.
     Suggested edit: update path AND remove the `(spec)` marker.
   ```

   ## 3. Still aspirational → leave the marker
   For each item:
   ```
   - File: docs/business-rules/ai-advisor-rules.md
     Line: 41
     Marker reason: FR6.x not scaffolded yet — no `backend/advisor/` module exists.
   ```

   ## Apply procedure
   1. Open each "Now true" item, delete the marker, commit `docs: clear resolved (verify) markers`.
   2. Open each "Wrong" item, fix the fact, commit `docs: correct stale claims`.
   3. Leave "Still aspirational" untouched.

OUTPUT (in chat):
1. The path to the written report (`docs/plans/marker-sweep-<yyyy-mm-dd>.md`).
2. The summary table (totals + trend).
3. The 3 highest-priority items in the "Wrong" bucket (fixing wrong claims is more urgent than clearing resolved ones).

DO NOT:
- Edit any file other than the new report under `docs/plans/`.
- Re-run the sweep multiple times in the same session — it produces a snapshot, not a live audit.
- Add new `(verify)` markers in this pass — that's the bootstrap prompts' job, not this sweep's.
- Treat absence of a file as proof that a feature is "still aspirational" if a similar file exists under a different name — flag it as WRONG, not aspirational.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| The report exists under `docs/plans/marker-sweep-<yyyy-mm-dd>.md` | `ls docs/plans/` |
| Totals add up — `A + B + C` equals the grep count | One quick arithmetic check. |
| Each "Now true" item cites a file:line that you can open and verify | Spot-check 3 items at random. |
| Each "Wrong" item describes both the doc claim AND the code reality | Spot-check 3 items. |
| The "Still aspirational" bucket isn't a dumping ground — every item has a real reason | Suspicious if > 80% of markers land here while real code has clearly been written. |

If the sweep classifies most markers as aspirational when the codebase is mature, the prompt was lazy — rerun in a fresh session emphasising that real code exists. If it classifies most markers as "now true" but cites wrong files, the prompt over-promised — rerun and demand line-number evidence.

---

## Applying the report

The report is a triage, not an edit. Apply it in **two separate commits** so the audit trail is clean:

```bash
# 1. Clear resolved markers
# (open each "Now true" item, delete the marker)
git add docs/ .claude/rules/ CLAUDE.md
git commit -m "docs: clear resolved (verify) markers from sprint-<NN>"

# 2. Fix wrong claims
# (open each "Wrong" item, fix the fact AND remove the marker)
git add docs/ .claude/rules/ CLAUDE.md
git commit -m "docs: correct stale claims found in marker sweep <yyyy-mm-dd>"
```

The report itself is committed too — it's the audit record:

```bash
git add docs/plans/marker-sweep-<yyyy-mm-dd>.md
git commit -m "docs: add marker sweep report <yyyy-mm-dd>"
```

When the project has zero `(verify)` markers left in a category, mention it in the next health check.

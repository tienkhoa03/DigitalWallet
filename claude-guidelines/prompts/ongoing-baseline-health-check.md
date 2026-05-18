# Ongoing — Baseline Health Check

**When to run:** monthly (after the first 3 months), and immediately if you suspect the AI artefacts have drifted from reality.
**Output:** a report at `docs/plans/baseline-health-check-<yyyy-mm-dd>.md` listing each [04-ongoing-development-workflow.md §5](../04-ongoing-development-workflow.md#5-health-checks-do-these-monthly) check with PASS / FAIL / WARN verdicts and concrete remediation actions.
**Estimated time:** 20–40 minutes. If it takes longer, the playbook has drifted — schedule a follow-up `claude-baseline-refresh` sprint.
**Prerequisites:**
- Bootstrap completed (see [03-initialization-workflow.md](../03-initialization-workflow.md)).
- At least one previous sweep/check report committed under [docs/plans/](../../docs/plans/) for trend comparison (optional but recommended).

---

## What this step produces

A markdown report with six rows — one per check from §5 of `04-ongoing-development-workflow.md` — and a "remediation backlog" appendix. The report is the audit artefact; the remediation list is the actionable output.

This prompt **does not edit artefacts** — it audits and reports. Remediations are applied in follow-up PRs (some are one-liners; others trigger the propagation prompt or the marker-sweep prompt).

---

## Copy-paste prompt

> Paste the following block, in its entirety, into a fresh Claude Code session opened at the project root.

```
You are running the monthly baseline health check for the AI artefacts.

INPUT:
- Read `claude-guidelines/04-ongoing-development-workflow.md` §5 (the canonical check list).
- For each row in the check table, run the indicated command (`grep`, `wc -l`, `find`, etc.) and capture the result.
- Read the previous health-check report (if any) from `docs/plans/baseline-health-check-*.md` to compute trends.

GOAL:
Produce a structured report at `docs/plans/baseline-health-check-<yyyy-mm-dd>.md` with one row per check and a remediation backlog the user can prioritise.

PROCEDURE:

For EACH of the six checks below, capture the measurement, assign a verdict, and write a one-line remediation hint.

1. (verify) marker trend
   - Measure: count via grep -rc -E "\(verify\)|spec — not yet implemented" docs/ .claude/rules/ CLAUDE.md  (sum across files).
   - Trend: compare with the count from the previous report.
   - Verdict:
     - PASS if trend is flat or down.
     - WARN if trend is up but within 10% of last report.
     - FAIL if trend is up > 10%.
   - Remediation hint: "Run prompts/ongoing-verify-marker-sweep.md" if WARN or FAIL.

2. ADR status hygiene
   - Measure: list ADRs in docs/decisions/ whose first 30 lines do NOT contain a `Status:` line, OR whose Status is not one of `Proposed | Accepted | Superseded`.
   - Verdict:
     - PASS if all ADRs have a valid Status.
     - FAIL if any ADR is missing a Status or has an invalid value.
   - Remediation hint: list the offending files; user must add the Status header in a follow-up commit.

3. Skill smoke test (quarterly, not monthly — but flag if quarter elapsed since last)
   - Measure: list skills under .claude/skills/ that have NOT been invoked since the last health check (best-effort — check git log of the skill's SKILL.md, plus any plan file under docs/plans/ that mentions the skill).
   - Verdict:
     - PASS if a smoke-test run is recent (< 90 days) or this is the first health check.
     - WARN if 60–90 days since last invocation.
     - FAIL if > 90 days.
   - Remediation hint: "Invoke each unused skill in a smoke-test session this sprint."

4. CLAUDE.md size
   - Measure: wc -l CLAUDE.md
   - Verdict:
     - PASS if ≤ 320 lines.
     - WARN if 321–400 lines.
     - FAIL if > 400 lines.
   - Remediation hint: "Move detail into docs/; CLAUDE.md should cite, not restate."

5. project-info.md vs CLAUDE.md consistency (quarterly — best-effort monthly)
   - Measure: read project-info.md §6 (NFR table) and CLAUDE.md "Non-Negotiable Invariants" block. Cross-check that every NFR row in §6 has a corresponding bullet in CLAUDE.md AND the wording is consistent.
   - Verdict:
     - PASS if every NFR maps cleanly.
     - WARN if a single NFR is paraphrased differently.
     - FAIL if any NFR is missing from CLAUDE.md or contradicted.
   - Remediation hint: "Run prompts/ongoing-propagate-project-info-change.md scoped to §6."

6. Dependency drift vs upgrade policy
   - Measure: parse top-level dependencies from any present `pom.xml`, `package.json`, or `pnpm-lock.yaml`. Compare each runtime dependency name against the table in .claude/rules/upgrade-policy.md §1.
   - Verdict:
     - PASS if every runtime dependency is mentioned in §1 (by name or via the framework it ships with).
     - FAIL if any runtime dependency is missing from §1 AND not justified by an ADR.
   - Remediation hint: "Add the missing dependency to upgrade-policy.md §1 OR write an ADR explaining its inclusion."
   - If neither file exists yet (early greenfield), record verdict = N/A and skip.

WRITE the report to `docs/plans/baseline-health-check-<yyyy-mm-dd>.md` with this structure:

# Baseline Health Check — <yyyy-mm-dd>

## Summary
| Check | Verdict | Measurement | Trend |
|---|---|---|---|
| 1. (verify) marker trend | PASS / WARN / FAIL | <count> markers | <↓N | flat | ↑N> |
| 2. ADR status hygiene | PASS / FAIL | <count> invalid | — |
| 3. Skill smoke test | PASS / WARN / FAIL | <count> stale skills | — |
| 4. CLAUDE.md size | PASS / WARN / FAIL | <N> lines | <vs prev> |
| 5. project-info vs CLAUDE.md | PASS / WARN / FAIL | <N> NFR mismatches | — |
| 6. Dependency drift | PASS / FAIL / N/A | <count> unlisted deps | — |

## Detailed findings
For each FAIL or WARN, give:
- The exact measurement (file paths, line numbers, dep names).
- The remediation hint with a concrete next prompt or file.

## Remediation backlog (ordered by severity)
1. <FAIL items first, then WARN>
2. ...

## Trend vs last report
- Previous report: `<path or "none">`
- Net direction: <improving | flat | regressing>
- Notes: <one paragraph if regressing>

OUTPUT (in chat):
1. The path to the written report.
2. The summary table.
3. The top 3 remediation items (in priority order).
4. A one-line recommendation: "Healthy", "Schedule remediations next sprint", or "Schedule a claude-baseline-refresh sprint within 2 weeks".

DO NOT:
- Apply any remediations — this prompt audits only.
- Skip a check because the data is inconvenient — record N/A with the reason instead.
- Compare against a non-existent prior report; if none, record "first health check" in the Trend column.
- Mark a check as PASS without the supporting measurement.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| The report exists under `docs/plans/baseline-health-check-<yyyy-mm-dd>.md` | `ls docs/plans/` |
| Every check has a verdict AND a measurement (no blank cells) | Skim the summary table. |
| FAILs have a concrete remediation hint pointing at a specific prompt or file | If "TBD" or "investigate further", push back. |
| The recommendation matches the verdict mix | All PASS → "Healthy"; ≥1 FAIL → "Schedule remediations" or "baseline-refresh sprint". |

If a check returned N/A for a reason that isn't obvious from the report ("project-info.md was archived 2 months ago"), the prompt was sloppy — rerun and demand the reason inline.

---

## Acting on the report

The remediation backlog drives follow-up PRs. Each item maps to one of the existing ongoing prompts or a small targeted edit:

| Failing check | Follow-up |
|---|---|
| 1. (verify) marker trend up | Run [ongoing-verify-marker-sweep.md](ongoing-verify-marker-sweep.md) |
| 2. ADR status hygiene | Manual edit of each offending ADR — set `Status:` line |
| 3. Skill smoke test | Open a fresh session, invoke each stale skill once with a realistic input |
| 4. CLAUDE.md size | Move overflowing content into `docs/`; replace with citations |
| 5. project-info vs CLAUDE.md mismatch | Run [ongoing-propagate-project-info-change.md](ongoing-propagate-project-info-change.md) scoped to the divergent section |
| 6. Dependency drift | Add the dep to `.claude/rules/upgrade-policy.md §1` OR write an ADR — whichever applies |

Commit the report itself first; remediations follow in subsequent PRs:

```bash
git add docs/plans/baseline-health-check-<yyyy-mm-dd>.md
git commit -m "docs: monthly baseline health check <yyyy-mm-dd>"
```

When everything is PASS for three consecutive monthly reports, downgrade the cadence to quarterly. When you next see a regression, return to monthly until you're green for three again.

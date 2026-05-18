# Ongoing — Add a New Artefact (skill / command / agent / rule)

**When to run:** you've identified a new repeatable procedure, user workflow, specialist domain, or coding contract that the bootstrap didn't produce. See [04-ongoing-development-workflow.md §4](../04-ongoing-development-workflow.md#4-adding-artefacts-after-bootstrap).
**Output:** one new file under [.claude/skills/](../../.claude/skills/), [.claude/commands/](../../.claude/commands/), [.claude/agents/](../../.claude/agents/), or [.claude/rules/](../../.claude/rules/), copied from a template and adapted.
**Estimated time:** 20–60 minutes per artefact depending on type.
**Prerequisites:**
- Bootstrap completed.
- You have a one-sentence description of what the artefact does and why the existing set doesn't cover it.

---

## What this step produces

Exactly one new artefact, plus any required cross-links from index files (e.g. a new skill referenced in `.claude/skills/README.md` if you keep one, or a new rule referenced from a sibling rule file when it's cross-cutting).

This prompt explicitly **does not** create multiple artefacts at once — combining a new skill + new agent + new command in one pass is how the playbook breaks. Run the prompt once per artefact.

---

## Copy-paste prompt

> Paste the following block, in its entirety, into a fresh Claude Code session opened at the project root.

```
You are adding a SINGLE new artefact to the AI playbook.

INPUT (the user MUST provide all five before invoking):
1. ARTEFACT TYPE: one of `skill | command | agent | rule`.
2. SLUG: kebab-case, e.g. `kafka-create-consumer`, `check-coverage`, `infra-developer`, `database-migrations`.
3. ONE-SENTENCE PURPOSE: what the artefact accomplishes.
4. TRIGGER PHRASES (skills/commands/agents only): natural-language utterances that should invoke it.
5. EXCLUSIONS (skills/agents only): what this artefact MUST NOT be used for.

If any of the five is missing, stop and ask the user for it. DO NOT fabricate defaults.

REFERENCE READING:
- `claude-guidelines/reference/what-each-artifact-is-for.md` — decision tree to confirm the artefact type is right.
- `claude-guidelines/04-ongoing-development-workflow.md` §4 — adding artefacts after bootstrap.
- For the chosen type, an existing artefact to copy as TEMPLATE:
  - skill   → `.claude/skills/code-review/` (or any sibling) — copy SKILL.md frontmatter + body.
  - command → `.claude/commands/make-plan.md` — copy frontmatter + body.
  - agent   → `.claude/agents/backend-developer.md` — copy frontmatter + body.
  - rule    → `.claude/rules/backend_coding.md` — copy section structure; new rule files are rare.

GOAL:
Produce one new file with the correct frontmatter, the right citations into existing rules and docs, and an exclusion section that prevents overlap with existing artefacts.

PROCEDURE:

1. VALIDATE the type against the decision tree in `reference/what-each-artifact-is-for.md`:
   - Repeatable procedure → skill.
   - User-invoked workflow → command.
   - Specialist persona → agent.
   - Coding contract → rule.
   If the user's stated TYPE conflicts with the decision tree, point it out and STOP. Ask the user to confirm or change TYPE.

2. CHECK for overlap. Read the index of the chosen folder:
   - For skills: list every existing `.claude/skills/*/SKILL.md`. If any has overlapping triggers or scope, list the conflict and STOP. Ask the user to either consolidate (extend the existing skill) or sharpen the new artefact's exclusions.
   - Same check for commands, agents, rules.

3. COPY the template. For the chosen type:
   - skill   → create `.claude/skills/<slug>/SKILL.md`. Copy the frontmatter shape and section structure from the closest sibling.
   - command → create `.claude/commands/<slug>.md`.
   - agent   → create `.claude/agents/<slug>.md`.
   - rule    → create `.claude/rules/<slug>.md` (only if cross-cutting; otherwise propose ADDING a section to an existing rule file instead).

4. FILL the frontmatter:
   - skill:    name, description, triggers (from INPUT.4), exclusions (from INPUT.5).
   - command:  name, description.
   - agent:    name, description (including exclusions from INPUT.5 — "Do NOT use for X"), tools, model.
   - rule:     status (`<!-- not-yet-adopted -->` if the rule isn't enforced yet), severity hint.

5. FILL the body:
   - Cite the rule files the artefact must respect — by section, not by restating.
   - For skills: write the procedure as numbered steps. Each step is a single tool action or a clearly bounded decision.
   - For commands: compose existing skills (`Skill <name>`) rather than re-implementing them.
   - For agents: state the stack scope, the rule files that bound it, and the skills it owns.
   - For rules: use MUST / MUST NOT / Prefer / Avoid. Anything weaker belongs in an ADR.

6. CROSS-LINK:
   - For a new skill, mention it in the relevant agent's "skills it owns" list (if it belongs to a specialist) and in `04-ongoing-development-workflow.md §1` if it's part of the daily loop.
   - For a new command, mention it in `04-ongoing-development-workflow.md §1` if it's part of the daily loop.
   - For a new agent, ensure its `description` lists exclusions for the other agents (and vice versa — update the older agent's description to exclude the new agent's territory).
   - For a new rule file, link from `code-review` skill's instructions so the new rule is checked in reviews.

7. TEST harness (skills/commands/agents only):
   - Print a "smoke-test invocation" the user can paste into a fresh session to verify the artefact works end-to-end.
   - Skill smoke test: a natural-language utterance + the expected first tool call.
   - Command smoke test: `/<slug>` + a one-paragraph happy-path expectation.
   - Agent smoke test: an `Agent` call with a realistic prompt and the expected first action.

OUTPUT (in chat):
1. The created file path.
2. A 3-bullet summary of frontmatter (name, scope, exclusions).
3. The cross-links you added (file path + line number for each).
4. The smoke-test invocation.
5. Any "consolidation suggestion" if the prompt detected an existing artefact that could absorb this scope instead.

DO NOT:
- Create more than one artefact in a single run. If the user asked for "a new skill AND a new command", stop and ask which to do first.
- Skip the exclusion section. An artefact without exclusions invites routing ambiguity.
- Restate coding rules inline — cite them.
- Edit any agent / skill / command / rule outside the cross-link update needed for the new artefact.
- Create a git commit.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| Exactly one new file was created | `git status` shows one new file (plus minor cross-link edits in the existing artefact folder). |
| Frontmatter has all required fields for the type | Compare to a sibling artefact. |
| The body cites rules instead of restating them | Look for "MUST" / "MUST NOT" sentences that aren't followed by a `(.claude/rules/...)` link — those are restatements. |
| Exclusions section names the existing artefacts it could be confused with | "Do NOT use for X (route to `<other-artefact>` instead)". |
| Smoke-test invocation works | Run it in a fresh session and confirm the first tool call matches the prompt's prediction. |
| Cross-links updated in sibling files | Spot-check 2 of the listed cross-links. |

If the smoke test fails, the artefact is unfinished — rerun the prompt with the failure description as input.

---

## Commit

After review:

```bash
git add .claude/<type>/<slug>*
# include cross-link edits from the prompt output
git add .claude/<other-files-edited>
git commit -m "claude: add <type> <slug>"
```

If this artefact replaces or supersedes an older one (rare but possible — e.g. a new agent supplants a stale one), do the deletion in a **separate** commit so the diff is reviewable.

---

## When to NOT use this prompt

- **For a small rule tweak** — edit the existing rule file directly. This prompt is for new artefacts, not edits.
- **For a one-off task** — if you only need to do this once, run it manually in a Claude session. Skills/commands/agents exist for *repeatable* work.
- **For a temporary scaffold** — don't enshrine throwaway procedures in `.claude/`. They become dead weight.

If in doubt, re-read [reference/what-each-artifact-is-for.md](../reference/what-each-artifact-is-for.md) — the decision tree there is the canonical filter.

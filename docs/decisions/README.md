# Architecture Decision Records (ADRs)

This page is the index for the project's ADRs. An **ADR** is a short, dated note capturing a single architecturally significant decision: the context, the alternatives considered, the option chosen, and the consequences the team accepts.

## When to write one

Open a new ADR when a decision:

- changes the public contract of a module, the wire protocol, or the persistence shape,
- changes how an NFR from [../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants) is enforced,
- introduces or removes a runtime dependency (LLM provider, message broker, cache, DB),
- supersedes a previously-Accepted ADR.

Routine code changes, lint-rule tweaks, and dependency version bumps do **not** need an ADR — they belong in the PR description.

## Naming convention

`000N-<slug>.md` — zero-padded sequential number, kebab-case slug, lowercase.

- The next free number is one higher than the highest currently-Accepted or Proposed ADR.
- The slug describes the decision in 2–6 words: `0001-jwt-signing-algorithm.md`, `0003-concurrency-strategy.md`.
- Numbers are never reused, even for rejected or superseded ADRs.

## Procedure

1. Copy [template.md](template.md) into a new file named `000N-<slug>.md`.
2. Set **Status** to **Proposed**, fill in the **Context**, list **Options considered**, and write a candidate **Decision**.
3. Link the ADR from the pull request that proposes it.
4. On merge: change **Status** to **Accepted** and update the **Date**. If the ADR replaces an older one, add `Supersedes: ../000M-<slug>.md` in the older record and `Superseded by: ../000N-<slug>.md` in the new one.
5. If the proposal is rejected: set **Status** to **Rejected** and leave it in place — rejected ADRs are valuable history.

## Index

The status column reflects the bootstrap state of each ADR. ADRs whose decisions are already committed in [../../project-info.md §10](../../project-info.md#10-open-architectural-decisions-adrs-to-write) are listed as **Proposed** here because the prose record is freshly seeded and has not yet been reviewed and merged as an explicit document; the rationale is to keep the seeded files and the merge timeline aligned. ADR #2 is genuinely **Proposed** in both the spec and the file.

| # | Title | Status | Date |
|---|---|---|---|
| 1 | [JWT signing algorithm](0001-jwt-signing-algorithm.md) | Proposed | 2026-05-13 |
| 2 | [LLM provider for the PFM advisor](0002-llm-provider.md) | Proposed | 2026-05-13 |
| 3 | [Concurrency strategy for wallet mutations](0003-concurrency-strategy.md) | Proposed | 2026-05-13 |
| 4 | [CQRS read-model for budgets](0004-cqrs-budget-read-model.md) | Proposed | 2026-05-13 |
| 5 | [Outbox publisher](0005-outbox-publisher.md) | Proposed | 2026-05-13 |
| 6 | [Multi-currency model](0006-multi-currency-model.md) | Proposed | 2026-05-13 |
| 7 | [Build tool](0007-build-tool.md) | Proposed | 2026-05-13 |
| 8 | [Frontend stack](0008-frontend-stack.md) | Proposed | 2026-05-13 |
| 9 | [RBAC roles in MVP](0009-rbac-roles.md) | Proposed | 2026-05-13 |
| 10 | [Fraud enforcement model](0010-fraud-enforcement-model.md) | Proposed | 2026-05-18 |

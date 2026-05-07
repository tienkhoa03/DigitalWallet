# docs/

**Single source of truth for humans AND AI.**

This directory describes **what the system is, what it does, and why**. For **how to code** inside the system (rules, style, security enforcement), see [.claude/rules/](../.claude/rules/) — the AI-enforcement layer (not yet present in this repo).

The two do not duplicate. They cross-link.

> **Repository status:** the codebase is not yet scaffolded. The project specification lives in [../README.md](../README.md) and the architectural distillation in [../CLAUDE.md](../CLAUDE.md). Every claim in this folder that depends on code is marked **(spec — not yet implemented)** or `(verify)` and must be replaced with verified file paths once implementation begins.

---

## Map

| Section | What you'll find |
|---|---|
| [onboarding/](onboarding/) | Get a new developer running locally |
| [architecture/](architecture/) | System overview, tech stack, module boundaries, auth flow |
| [api/](api/) | Every endpoint the backend exposes |
| [database/](database/) | ERD, table descriptions, migration history |
| [domain-knowledge/](domain-knowledge/) | What the product is, who uses it, and why |
| [business-rules/](business-rules/) | The rules the running app enforces |
| [testing/](testing/) | Test strategy and how to run |
| [decisions/](decisions/) | ADRs — decisions with context and consequences |
| [plans/](plans/) | Implementation plans (populated later) |

A `terminologies/` folder is intentionally omitted — the product does not curate a glossary or controlled vocabulary.

## How to use this folder

- **New developer?** Start at [onboarding/](onboarding/).
- **Adding a feature?** Read the relevant [business-rules/](business-rules/) page, then [api/](api/) and [database/](database/).
- **Making an architectural change?** Draft an ADR in [decisions/](decisions/) before you implement.

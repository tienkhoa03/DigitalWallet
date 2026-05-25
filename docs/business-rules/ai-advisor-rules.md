# Epic 6 — AI Advisor rules

This page captures the per-FR rules for Epic 6 (FR6.1–FR6.3) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs). The LLM provider itself is an open decision tracked in [../decisions/0002-llm-provider.md](../decisions/0002-llm-provider.md).

## FR6.1 — End-of-month analysis

- **Rule:** `POST /advisor/analyze` returns HTTP 202 with a `request_id` immediately. The actual LLM call is performed off the HTTP thread; the final advice is delivered as a WebSocket frame on the user's notification channel.
- **Why:** NFR8 — LLM calls are slow and expensive; HTTP threads must not block. The behaviour is the canonical Asynchronous Request-Reply pattern.
- **Enforced in:** `advisor/api/` resource; `advisor/service/` orchestration; `advisor/client/` LLM client; user-scoped WebSocket channel. `(verify)`
- **Failure mode:**
  - Circuit breaker open → HTTP 503 `error_key: "advisor.circuit_open"` (no retry semantics on the client; user-visible "advice unavailable" state — no rule-based fallback in MVP, [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)).
  - Rate limit exceeded (>5/hour/user) → HTTP 429 `error_key: "ratelimit.exceeded"`.
  - Requested month not closed → HTTP 422 `error_key: "advisor.month_not_ready"`.
- **Frontend shortcut:** The "Get advice" button transitions to a spinner on 202 and resolves when the WebSocket frame arrives.

## FR6.2 — Personalised advice

- **Rule:** The WebSocket reply is the personalised advice from the LLM, scoped to the user's own anonymised spending data. The reply carries the `request_id` from FR6.1.
- **Why:** FR6.2 — tailored advice; NFR8 — async delivery.
- **Enforced in:** `advisor/service/` correlation by `request_id`; `advisor/ws/` user fan-out. `(verify)`
- **Failure mode:** A reply with no matching `request_id` on the receiving client is dropped; replies are not fanned out to other users (server-side scoped to `user_id`).
- **Frontend shortcut:** Loading state is keyed on `request_id`; stale replies (e.g. user navigated away and back) are reconciled against the stored last-known request.

## FR6.3 — Auto-adjust plan

- **Rule:** `POST /advisor/auto-adjust` follows the same async request-reply protocol (HTTP 202 + WebSocket result) and returns a proposed `buckets[]` shape. The proposal is **advisory**: applying it requires an explicit `POST /budgets` for the next month by the user (no auto-commit).
- **Why:** NFR8; FR6.3 — LLM suggests, user decides.
- **Enforced in:** `advisor/api/` resource; `advisor/service/`. `(verify)`
- **Failure mode:** Same as FR6.1.
- **Frontend shortcut:** The next-month budget editor pre-fills from the proposal but every field is editable before submit.

## Cross-cutting

- **Rule (anonymisation):** Prompts sent to the LLM must not contain user identifiers (`user_id`, email, name). Only aggregated amounts and category labels are sent.
- **Why:** [../../project-info.md §8](../../project-info.md#8-security-baseline) — LLM data retention is provider-dependent and the answer is open ([../../project-info.md §16](../../project-info.md#16-open-questions-to-answer-before-bootstrapping) item 15).
- **Enforced in:** `advisor/service/` prompt builder; a unit test asserts the produced prompt contains no PII strings `(verify)`.
- **Failure mode:** Failed assertion is a P0 fix-forward issue.
- **Frontend shortcut:** None — server invariant.

- **Rule (circuit breaker):** Outbound LLM calls are wrapped in a SmallRye `@CircuitBreaker`. When open, the advisor surfaces the "unavailable" state and does not fall back to a rule-based heuristic.
- **Why:** NFR8; [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope) — explicit non-goal that the advisor degrades to heuristics.
- **Enforced in:** `advisor/client/` LLM client. `(verify)`
- **Failure mode:** Circuit open returns HTTP 503 / WebSocket "unavailable" payload.
- **Frontend shortcut:** A clear "Advisor unavailable, try later" banner on the advisor view.

- **Rule (rate limit):** `POST /advisor/*` is limited to 5 requests per hour per user via the Redis token bucket — independent of the circuit breaker.
- **Why:** [../../project-info.md §8](../../project-info.md#8-security-baseline) — LLM cost control as a second layer on top of NFR8.
- **Enforced in:** Rate-limit middleware in `shared/`. `(verify)`
- **Failure mode:** HTTP 429 `error_key: "ratelimit.exceeded"` with `Retry-After`.
- **Frontend shortcut:** The "Get advice" button disables for the displayed `Retry-After` interval.

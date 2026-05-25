# 0011 — Observability Stack

## Status

`Proposed` — flips to `Accepted` at the close of Phase 9 of [`implementation-plan-mvp-master.md`](../plans/implementation-plan-mvp-master.md).

## Date

2026-05-25

## Deciders

TBD (drafted alongside the slimmed MVP master plan on 2026-05-25).

## Context

[../../project-info.md §17.2](../../project-info.md#172-observability) lists "Observability — metrics sink and tracing stack TBD" as one of the spec gaps that "blocks the audit-log + SOC 2 invariants from covering operational signals." The MVP master plan owns the resolution: without metrics and structured logs we cannot measure the [§17.1 performance budget](../../project-info.md#171-performance-budget) (`/transfers` ≤ 200 ms P95) and we cannot debug production incidents.

The relevant constraints:

- MVP is single-host docker-compose ([../../project-info.md §4.6](../../project-info.md#46-deployment)). No Kubernetes, no managed observability service in scope.
- The mandated stack is Quarkus 3.x LTS ([../../.claude/rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md#1-supported-baselines)) — observability choices should fit naturally as Quarkus extensions per [../../.claude/rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code).
- The logger contract in [../../.claude/rules/backend_coding.md §11](../../.claude/rules/backend_coding.md#11-logging) and [security.md §7](../../.claude/rules/security.md#7-sensitive-data-exposure) restricts what may be logged (no full email / balance / `Idempotency-Key` / LLM bodies). Any logging change must continue to honour these.
- The slimmed MVP scope (Epic 6 cut, no Kafka consumers, no WS endpoints) means we don't need distributed tracing across services yet — a single application + Postgres + Redis is the entire surface.

## Options considered

- **Option A — Quarkus Micrometer + Prometheus scrape endpoint + structured JSON stdout logs (chosen).** Add `quarkus-micrometer-registry-prometheus` to expose `/q/metrics`. Configure Quarkus to emit JSON-structured logs to stdout (`quarkus.log.console.json=true`). Operators scrape metrics with any Prometheus / Grafana / VictoriaMetrics that fits their host; logs are read with `docker compose logs` or any log shipper. **Pros:** native Quarkus extension, zero extra container, fits single-host MVP, lowest operational overhead, scales out later by pointing a real Prometheus at the endpoint. **Cons:** no distributed tracing today, no log aggregation UI bundled — operators stitch their own dashboards.
- **Option B — Add OpenTelemetry (OTLP) exporter + Tempo / Jaeger + Loki + Prometheus.** Wire `quarkus-opentelemetry`, export metrics + traces + logs over OTLP, run Grafana / Tempo / Loki / Prometheus in docker-compose. **Pros:** future-proof, single industry-standard wire format, traces unblock cross-consumer debugging when post-MVP epics ship. **Cons:** four extra containers on a single host, significant configuration surface, partially-traced application (no consumers, no LLM, no WS in MVP) means most of the trace value is unrealised, runs hot on a laptop.
- **Option C — Defer observability entirely; rely on `docker compose logs` and ad-hoc `EXPLAIN ANALYZE`.** **Pros:** zero work. **Cons:** Phase 9's §17.1 perf budget cannot be measured; the spec gap stays open; debugging the fraud pre-check in isolation requires log-line counting. Violates the user's direction (2026-05-25) to resolve open ADRs as part of MVP.

## Decision

Option A. Ship Quarkus Micrometer + Prometheus endpoint + structured JSON stdout logs. Re-evaluate when the first Kafka consumer ships post-MVP — at that point distributed tracing earns its keep and Option B becomes worth the operational tax.

## Consequences

- **Easier:**
  - Phase 9 has a concrete deliverable — wire the extension, expose `/q/metrics`, switch the log format, capture real `/transfers` P95.
  - Operators on the docker-compose stack get metrics without a new container; any external Prometheus can scrape the endpoint.
  - Structured JSON logs are immediately searchable by `jq` and survive a future log-shipper without reformatting.
- **Harder:**
  - No bundled dashboard — first-time setup means writing PromQL or Grafana JSON by hand if/when a Grafana lands.
  - Trace correlation across the (eventual) HTTP → consumer → WS path is not solved today; teams that need it post-MVP take the migration cost to OTLP.
- **Live with:**
  - The MVP cannot answer "show me every operation in this request" beyond a single `X-Request-Id` log key — acceptable because there are no async consumers in MVP.
  - No SLO alerting wired in MVP; the dev-target in §17.1 is a one-shot measurement, not a continuous gate.
- **Revisit if:**
  - The first Kafka consumer (likely async fraud) ships post-MVP — distributed tracing across producer + consumer becomes load-bearing.
  - Production deployment is approved (out of MVP scope per [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)) — a managed observability service or full LGTM stack becomes warranted.
  - Compliance asks (SOC 2 readiness audit) demand audit-log + metrics correlation that the simple stack can't supply.

## References

- Related ADRs: [0009-rbac-roles.md](0009-rbac-roles.md) (audit log deferred — independent of metrics path).
- Spec sections: [../../project-info.md §17.1](../../project-info.md#171-performance-budget), [../../project-info.md §17.2](../../project-info.md#172-observability), [../../project-info.md §8](../../project-info.md#8-security-baseline).
- Rule sources: [../../.claude/rules/backend_coding.md §11](../../.claude/rules/backend_coding.md#11-logging), [../../.claude/rules/security.md §7](../../.claude/rules/security.md#7-sensitive-data-exposure), [../../.claude/rules/upgrade-policy.md §3](../../.claude/rules/upgrade-policy.md#3-backend-upgrade-guardrails-for-new-code).
- External: Quarkus Micrometer extension (`io.quarkus:quarkus-micrometer-registry-prometheus`), OpenTelemetry specification (deferred).

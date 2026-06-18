# 0012 — Hexagonal architecture (ports & adapters)

## Status

Accepted

## Date

2026-06-10

## Deciders

TBD

## Context

DigitalWallet's centre of gravity is its domain logic: wallet mutations, the two-leg transfer invariant, fraud rules, budget accounting, and the money type. The hybrid-concurrency, outbox, idempotency, event-time, and CQRS invariants (NFR1–NFR9) are business contracts that must not be tangled into framework plumbing. The original organising principle — feature-based + layered (`api/` · `service/` · `persistence/` · `consumer/` · `event/`) — kept frameworks (JAX-RS, Hibernate/Panache, SmallRye Kafka, Redis client) reachable from the layers that hold business rules, which makes the domain harder to test in isolation and couples use cases to infrastructure. We want a framework-free `domain/` and `application/` layer with frameworks confined to isolated adapters, while preserving the modular-monolith boundaries (one hexagon per feature module, `shared/` as the cross-cutting kernel) described in [../../project-info.md §3.1](../../project-info.md#31-module--package-organization).

## Options considered

- **Option A — keep feature-based + layered** (`api/` · `service/` · `persistence/` · `consumer/` · `event/` per module). Familiar and already scaffolded; but frameworks leak into the service layer, the domain model doubles as the JPA entity, and unit-testing business rules requires mocking infrastructure rather than depending on ports.
- **Option B — single global hexagon** for the whole backend (one `domain/`, one `application/`, one `adapter/` shared across all features). Maximises domain reuse and a single dependency rule; but it dissolves the modular-monolith boundaries, makes Kafka-decoupled cross-module collaboration ambiguous, and grows into a god-package as features land.
- **Option C — per-module hexagon (ports & adapters)** **(chosen)**. Each feature module is its own hexagon with the dependency rule pointing inward; modular-monolith boundaries are preserved and cross-module collaboration stays over Kafka or a `shared/` port.

## Decision

Each feature module (`account`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`) is a self-contained hexagon with the layout:

```
backend/src/main/java/com/digitalwallet/<module>/
├── domain/                       # framework-free: entities, value objects, domain services, rules
├── application/
│   ├── port/in/                  # inbound ports = use-case interfaces
│   ├── port/out/                 # outbound ports = SPIs (load/save, publish, lock, fraud counter, idempotency …)
│   └── service/                  # use-case implementations = application services
└── adapter/
    ├── in/web/                   # inbound REST adapter: JAX-RS resources + request/response DTOs + mappers
    ├── in/messaging/             # inbound Kafka adapter: @Incoming consumers
    └── out/
        ├── persistence/          # outbound JPA adapter: <Name>Entity + PanacheRepository + <Name>PersistenceAdapter
        ├── redis/                # outbound Redis adapter (locks, sliding-window counters, idempotency, read-model)
        ├── messaging/            # outbound Kafka adapter (outbox poller is the sole producer; lives in shared/)
        └── llm/                  # outbound LLM adapter (advisor module only)
```

Dependencies point inward only (`adapter` → `application` → `domain`); `domain/` depends on nothing and carries no `jakarta.persistence` annotations. Frameworks appear only in `adapter/`. Application services depend on outbound ports (`port/out`), never on concrete adapters; inbound web/messaging adapters depend on inbound ports (`port/in`). `shared/` holds the domain kernel (money type), the exception/security/validation infrastructure, and the cross-cutting outbound adapters (idempotency store, transactional-outbox poller, rate-limit middleware, Redis distributed-lock helper). The NFR1 hybrid-concurrency ordering (Redis lock → DB `PESSIMISTIC_WRITE` → outbox write → commit → release) and the NFR9 synchronous fraud pre-check are unchanged — they now live in the application service.

This supersedes the "feature-based + layered" organising principle previously stated in project-info.md §3.1.

## Consequences

- **Easier:** the domain and application layers are framework-free and unit-testable without mocking infrastructure; swapping or adding an adapter (e.g. a second outbound channel) touches only `adapter/`; the dependency rule makes accidental framework leakage a compile-time concern.
- **Harder:** more packages and an explicit mapper between the domain model and the JPA `<Name>Entity`; engineers must internalise the inward-dependency rule and the port/adapter split rather than calling repositories directly from services.
- **Live with:** the domain↔entity mapping boilerplate, and the per-module duplication of the hexagon skeleton.
- **Revisit if:** the per-module hexagon proves too heavyweight for thin modules (e.g. `dashboard`), or cross-module sharing pressure suggests promoting a shared application layer.

## References

- Related ADRs: [0003-concurrency-strategy](0003-concurrency-strategy.md), [0005-outbox-publisher](0005-outbox-publisher.md), [0010-fraud-enforcement-model](0010-fraud-enforcement-model.md)
- Spec sections: [../../project-info.md §3.1](../../project-info.md#31-module--package-organization), [../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants)
- Rules: [../../.claude/rules/backend_coding.md §1](../../.claude/rules/backend_coding.md#1-project-structure)
- External: Alistair Cockburn, "Hexagonal Architecture (Ports & Adapters)"; prior art on the Onion / Clean architecture family.

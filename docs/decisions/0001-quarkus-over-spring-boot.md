# 0001 — Quarkus over Spring Boot and plain Jakarta EE

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author (per [../../README.md §2](../../README.md))

## Context

The project specification ([../../README.md §2](../../README.md)) requires a Java 21 backend stack built on Jakarta-namespaced APIs (JAX-RS, CDI, JPA, Jakarta Transactions, WebSocket). Two ecosystems satisfy that constraint: a plain Jakarta EE 10 runtime (WildFly, Payara, Open Liberty) and Quarkus, which is built on the same Jakarta-spec foundations but adds opinionated extensions, build-time augmentation, dev-mode live reload, and first-class Kafka/Reactive-Messaging support. Spring Boot, despite its size, is excluded because it does not run on Jakarta-spec APIs in a portable way and the project explicitly forbids it. The decision was made before any code was written; this ADR records the rationale so future contributors do not silently drift toward another framework.

## Decision

We will build the backend on **Quarkus** (current LTS / supported stream) running on the standard JVM (native image is optional and out of scope for the MVP).

The selected extensions for the MVP:

- `quarkus-resteasy` (RESTEasy Classic — synchronous, blocking; matches the ACID transaction path)
- `quarkus-resteasy-jackson`
- `quarkus-hibernate-orm-panache` (Panache repository pattern over Hibernate ORM)
- `quarkus-jdbc-postgresql`
- `quarkus-flyway`
- `quarkus-narayana-jta` (transitively pulled in by Hibernate ORM)
- `quarkus-smallrye-reactive-messaging-kafka` (producer + consumer)
- `quarkus-redis-client` (Redisson Quarkus extension is added later if the advanced distributed-lock path is implemented)
- `quarkus-websockets-next` (admin fraud-alert push)
- `quarkus-hibernate-validator` (Bean Validation 3.0)
- `quarkus-smallrye-openapi` (API docs)
- `quarkus-smallrye-health` and `quarkus-micrometer-registry-prometheus` (operational endpoints)
- Test: `quarkus-junit5`; Postgres and Kafka run via Testcontainers — H2 is **not** used (see [../../.claude/rules/testing.md §2.4](../../.claude/rules/testing.md))

## Options considered

### Option A — Spring Boot 3
- Pros: very large ecosystem, lots of tutorials, conventional choice for new JVM projects.
- Cons: not Jakarta-spec native in the way the project requires; mixing Spring with Jakarta-spec code creates a hybrid that confuses contributors; explicitly out of scope.

### Option B — Plain Jakarta EE 10 on a standard runtime (WildFly / Payara / Open Liberty)
- Pros: matches the spec's API list exactly; uses platform-standard APIs that are stable across runtimes; demonstrates competence with the underlying specifications rather than a single vendor.
- Cons: heavier dev loop (no live reload comparable to Quarkus dev mode); more glue for configuration, observability, and Kafka integration; larger startup footprint and memory; less integrated tooling for containerization.

### Option C — Quarkus  *(chosen)*
- Pros: Jakarta-EE-aligned (uses `jakarta.*` packages — JAX-RS, CDI, JPA, Bean Validation, Transactions); dev-mode live reload accelerates iteration; first-class SmallRye Reactive Messaging for Kafka; built-in OpenAPI / Health / Metrics; container-image and Docker-Compose tooling; native-image option preserved if performance becomes a concern; Panache reduces persistence boilerplate without sacrificing layered architecture.
- Cons: opinionated build-time augmentation has its own learning curve; some idioms (`@QuarkusTest`, `application.properties` over per-runtime XML) differ from a "plain EE" deployment; native image has reflection caveats (mitigated by JVM-mode default).

## Consequences

- Easier: dev iteration (live reload), Kafka wiring, OpenAPI generation, container packaging, persistence boilerplate (Panache).
- Harder: deviating from Quarkus idioms is a smell — extensions are the supported integration path. New libraries should be vetted against the Quarkus extension catalog before being added as plain Maven deps.
- Live with: a single runtime vendor (Red Hat / Quarkus). Portability across Jakarta-EE runtimes is no longer a goal of this project.
- Revisit if: Quarkus drops Jakarta-spec compatibility for a feature we depend on, or runtime cost / native-image limitations block a release.

## References

- [../../README.md §2](../../README.md) — explicit stack mandate.
- [../../CLAUDE.md](../../CLAUDE.md) — restates the constraint.
- [../../.claude/rules/backend_coding.md](../../.claude/rules/backend_coding.md) — Quarkus coding contract.
- [../../.claude/rules/upgrade-policy.md](../../.claude/rules/upgrade-policy.md) — version baselines.

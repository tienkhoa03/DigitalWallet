# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

This repository currently contains only the project specification ([README.md](README.md)) and IntelliJ project files. No source code, build configuration (pom.xml/build.gradle), or `docker-compose.yml` exists yet. Treat the README as the design contract: when scaffolding new code, conform to the stack and architecture below rather than introducing alternatives.

## Tech Stack (Mandated by Spec)

- **Backend:** Java 21 + Quarkus (RESTEasy Classic for JAX-RS, CDI/ArC, Hibernate ORM with Panache, Narayana JTA, Quarkus WebSockets Next). Do not substitute Spring Boot or a plain Jakarta-EE runtime — the project is committed to Quarkus.
- **Persistence:** PostgreSQL with Flyway migrations (`quarkus-flyway`); Hibernate ORM with Panache repositories.
- **Messaging:** Apache Kafka via SmallRye Reactive Messaging — used as the seam between the synchronous transaction path and the asynchronous fraud engine.
- **Cache / Coordination:** Redis (idempotency keys, distributed locks via Redisson, rate limiting, short-window fraud counters), accessed through `quarkus-redis-client` or the Redisson Quarkus extension.
- **Frontend:** Angular 17+, Tailwind CSS, RxJS, with the browser WebSocket client connecting to the Quarkus WebSockets Next endpoint.
- **Testing:** JUnit 5 + Mockito + `@QuarkusTest`; the spec mandates ≥80% coverage at the service layer for balance-handling classes.
- **Deployment:** Docker + Docker Compose (Quarkus container image via `quarkus-container-image-jib` or `quarkus-container-image-docker`).

## Architecture: Two Parallel Streams

The system is deliberately split into two streams that must not block each other. Understanding this split is essential before changing any transaction-path code.

### Synchronous stream (core banking) — must be ACID and fast
Path: Angular → RESTEasy Classic (JAX-RS) → Service → Hibernate ORM/Postgres → Kafka publish → 200 OK.

The transfer service does exactly four things inside the transaction boundary:
1. Check Redis for the `Idempotency-Key`; short-circuit with 200 if already processed.
2. Open DB transaction, lock both wallet rows (`SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`, or Redisson distributed lock for the advanced path).
3. Debit sender, credit receiver, write two `transaction_history` rows, commit.
4. Publish a JSON event to Kafka topic `transaction-events` via SmallRye Reactive Messaging (`@Channel` Emitter).

**Do not** put fraud analysis, notifications, or anything non-essential into this path — that violates NFR5 (Decoupling).

### Asynchronous stream (fraud engine) — runs on Kafka consumer threads
Path: `transaction-events` consumer (SmallRye `@Incoming`) → rule evaluation (using Redis/DB for short-window counters) → on violation, emit to `fraud-alerts` (SmallRye `@Outgoing`/`Emitter`) → WebSockets Next endpoint broadcasts to admin clients.

Rules to implement:
- **Velocity:** >5 transactions from one account within 1 minute.
- **Volume:** cumulative small transactions exceeding a threshold (e.g. >$50k) within 1 hour.

### Feature-based + layered organization
Group code by feature module (`wallet`, `transaction`, `fraud`, `admin`) and within each feature keep the standard layers separated: `api/` (JAX-RS resources), `service/` (CDI beans with `@Transactional`), `persistence/` (JPA entities + Panache repositories), `event/` (SmallRye Reactive Messaging producers/consumers, WebSockets Next endpoints).

## Non-Negotiable Invariants

These are called out in the spec as mandatory; treat any change that weakens them as a regression:

- **Concurrency:** Balance updates always go through pessimistic locking or a distributed lock. Never read-modify-write a balance without one.
- **Consistency:** DB commit and Kafka publish must not silently diverge. The Outbox Pattern is the preferred fix; if not implementing it yet, at minimum order operations so that a Kafka publish failure does not leave money moved without an event (and document the chosen tradeoff).
- **Idempotency:** Transfer endpoints require an `Idempotency-Key` HTTP header; the same key must produce one balance change regardless of retries.
- **Decoupling:** Fraud detection lives only in the Kafka consumer, never on the request thread.

## Commands

No build tooling is committed yet. When introducing it, use Maven with the Quarkus platform BOM (the standard Quarkus archetype: `mvn io.quarkus.platform:quarkus-maven-plugin:create`). Add the standard Quarkus lifecycle commands here once they exist:

- `./mvnw quarkus:dev` — dev mode with live reload
- `./mvnw test` — unit tests
- `./mvnw verify` — unit + integration tests (incl. Testcontainers)
- `./mvnw package` — build the runner JAR (or native image with `-Dnative`)
- `docker compose up` — Postgres, Kafka, Redis, plus the Quarkus app

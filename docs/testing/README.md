# Testing

> For rules that test code itself must follow (naming, annotations, assertion style), see `.claude/rules/testing.md` once that exists. This page describes **what the test infrastructure is**.

## §1 Test locations (spec — not yet implemented)

```
backend/
├── src/main/java/...   # production code
└── src/test/java/...   # JUnit 5 + Mockito
frontend/
└── src/...             # Karma or Jest (verify — Angular CLI default is Karma + Jasmine)
```

## §2 How to run (spec — not yet implemented)

| Goal | Command |
|---|---|
| Backend full suite | `mvn test` (verify) |
| Backend single class | `mvn -Dtest=TransferServiceTest test` (verify) |
| Backend single method | `mvn -Dtest=TransferServiceTest#deductsSenderAndCreditsReceiver test` (verify) |
| Backend coverage report | `mvn verify` with JaCoCo (verify) |
| Frontend full suite | `npm test` (verify) |
| Frontend single spec | `npm test -- --include='**/transfer.service.spec.ts'` (verify) |

## §3 What each layer covers

| Layer | Runner | Notes |
|---|---|---|
| Unit (service) | JUnit 5 + Mockito | Mandatory per [NFR4](../../README.md). Mocks repositories and the Kafka producer. |
| Persistence | JUnit 5 + Testcontainers (verify) | Real Postgres in a container; verifies SQL, locks, and migration application. |
| Kafka consumer | JUnit 5 + embedded broker or Testcontainers Kafka (verify) | Verifies fraud-rule evaluation against a known event sequence. |
| API / contract | (verify — REST Assured candidate) | Round-trip JSON to JAX-RS resources. |
| Frontend unit | Karma/Jest (verify) | Component + RxJS pipe behaviour. |
| End-to-end | not yet specified | Open. |

## §4 Coverage targets

- **Service layer (balance handling): ≥ 80% line coverage** — mandated by [NFR4](../../README.md).
- Other layers: no explicit floor; aim for meaningful coverage on rule logic and persistence.

## §5 Test fixtures

- Backend: under `backend/src/test/resources/` (verify); JSON event fixtures named for the scenario (e.g., `transfer_velocity_breach.json`).
- Frontend: co-located `*.spec.ts` next to the component, with shared fixtures under `src/testing/` (verify).
- To add a fixture: place it next to the test that consumes it, name it for the scenario (not the data), and reference it from at least one assertion-bearing test.
